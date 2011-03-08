(ns swank.core.cdt-backends
  (:refer-clojure :exclude [next])
  (:require [com.georgejahad.cdt :as cdt]
            [swank.util.concurrent.mbox :as mb]
            [swank.core :as core]
            [swank.util.concurrent.thread :as st])
  (:use swank.core.debugger-backends))

(defn match-name [thread-name]
  #(re-find (re-pattern (str "^" thread-name "$")) (.getName %)))

(defn get-all-threads []
  (map key (Thread/getAllStackTraces)))

(defn get-thread [thread-name]
  (first (filter
          (match-name thread-name)
          (get-all-threads))))

(def control-thread (atom nil))

(defn set-control-thread []
  (reset! control-thread
          (get-thread "Swank Control Thread")))

(def cdt-thread-group-name #"Clojure Debugging Toolkit")
(defonce cdt-thread-group (ThreadGroup. (str cdt-thread-group-name)))
(def system-thread-group-names #{#"JDI main" #"JDI \[\d*\]" #"system"
                                 cdt-thread-group-name})
(def system-thread-groups (atom []))
(defn system-thread-group? [g]
  (some #(re-find % (.name g)) system-thread-group-names))

(defn set-system-thread-groups []
  (reset! system-thread-groups
          (filter system-thread-group?
                  (cdt/all-thread-groups))))

(defn get-system-thread-groups [] @system-thread-groups)

(def system-thread-names #{#"^CDT Event Handler$" #"^Swank Control Thread$" #"^Read Loop Thread$"
                           #"^Socket Server \[\d*\]$"})
(defn system-thread? [t]
  (some #(re-find % (.name t)) system-thread-names))

(defn get-system-threads []
  (filter system-thread? (cdt/list-threads)))

(defn get-non-system-threads []
  (remove system-thread? (cdt/list-threads)))

(defn get-env [e]
  (condp = (second (re-find #"^(.*)Event@" (str e)))
      "Breakpoint"
    (list (str "CDT " e) "From here you can: e/eval, v/show source, s/step, x/next, o/exit func" '((:show-frame-source 0)))
    "Step"
    (list (str "CDT " e) "From here you can: e/eval, v/show source, s/step, x/next, o/exit func" '((:show-frame-source 0)))
    "Exception"
    (list (str "CDT " e) "From here you can: e/eval, v/show source" '((:show-frame-source 0)))))

(defn event-data [e]
  {:thread (.uniqueID (cdt/get-thread e))
   :env (get-env e)})

(defn default-handler [e]
  (when-not @control-thread
    (set-control-thread))

  (prn "gbj43" `(:cdt-rex ~(pr-str `(swank.commands.basic/sldb-cdt-debug ~(event-data e)))  true))
  (mb/send @control-thread
           `(:cdt-rex ~(pr-str `(swank.commands.basic/sldb-cdt-debug ~(event-data e))) true)))

(defn display-background-msg [s]
  (mb/send @control-thread (list :eval-no-wait "slime-message" (list "%s" s))))

(defn backend-init []
  (cdt/cdt-attach-pid)
  (cdt/set-handler cdt/exception-handler default-handler)
  (cdt/set-handler cdt/breakpoint-handler default-handler)
  (cdt/set-handler cdt/step-handler default-handler)
  (cdt/create-thread-start-request)
  (reset! cdt/CDT-DISPLAY-MSG display-background-msg)
  (set-control-thread)
  (set-system-thread-groups))

(defmethod swank-eval :cdt [form]
           (cdt/safe-reval (:thread *debugger-env*)
                           @(:frame *debugger-env*) form true identity))

(defn get-full-stack-trace []
   (.getStackTrace (get-thread #_(.getName @control-thread)
                               (.name (:thread *debugger-env*)))))

(defmethod get-stack-trace :cdt [n]
           (println "gbj31" (type n) n)
           (reset! (:frame *debugger-env*) n)
           (nth (get-full-stack-trace) n))

(defmethod exception-stacktrace :cdt [_]
           (println "gbj6")
           (map #(list %1 %2 '(:restartable nil))
                (iterate inc 0)
                (map str (get-full-stack-trace))))

(defmethod debugger-condition-for-emacs :cdt []
           (println "gbj5")
           (:env *debugger-env*))

(defn exception? []
  (.startsWith (first (:env *debugger-env*)) "CDT Exception"))

(defn get-quit-exception []
  (if (exception?)
    core/debug-abort-exception
    core/debug-cdt-continue-exception))

(defmethod calculate-restarts :cdt [_]
           (let [quit-exception (get-quit-exception)
                 restarts [(core/make-restart :quit "QUIT" "Quit to the SLIME top level"
                                     (fn [] (throw quit-exception)))]
        restarts (core/add-restart-if
                  (pos? core/*sldb-level*)
                  restarts
                  :abort "ABORT" (str "ABORT to SLIME level " (dec core/*sldb-level*))
                  (fn [] (throw core/debug-abort-exception)))]
    (into (array-map) restarts)))

(defmethod build-backtrace :cdt [start end]
           (doall (take (- end start) (drop start (exception-stacktrace nil)))))

(defmethod eval-string-in-frame-internal :cdt [string n]
  (reset! (:frame *debugger-env*) n)
  (cdt/safe-reval (:thread *debugger-env*)
                  @(:frame *debugger-env*) (read-string string) true identity))

(defmacro make-cdt-method [name func]
  `(defmethod ~name :cdt []
              (println "gbj " '~func  (:thread *debugger-env*) ~(ns-resolve (the-ns 'com.georgejahad.cdt) func))
              (~(ns-resolve (the-ns 'com.georgejahad.cdt) func)
               (:thread *debugger-env*))
              true))

(make-cdt-method step step)
(make-cdt-method next step-over)
(make-cdt-method finish finish)
(make-cdt-method continue continue-thread)

(defmethod show-source :cdt []
#_           (core/send-to-emacs '(:eval-no-wait "sldb-show-frame-source" (0))))

(defmethod set-dbe-thread :cdt-rex [_ f]
           (binding [st/*new-thread-group* cdt-thread-group]
             (f)))

(defmethod line-bp :cdt [file line]
           (cdt/line-bp file line
                        (get-non-system-threads)
                        (get-system-thread-groups) true))

(backend-init)

