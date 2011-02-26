(ns swank.core.cdt-backends
  (:refer-clojure :exclude [next])
  (:require [com.georgejahad.cdt :as cdt]
            [swank.util.concurrent.mbox :as mb]
            [swank.core :as core])
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

(defn event-data [e]
  (condp = (second (re-find #"^(.*)Event@" (str e)))
      "Breakpoint"
    (list (str "CDT " e) "From here you can: e/eval, v/show source, s/step, x/next, o/exit func" nil)
    "Step"
    (list (str "CDT " e) "From here you can: e/eval, v/show source, s/step, x/next, o/exit func" nil)
    "Exception"
    (list (str "CDT " e) "From here you can: e/eval, v/show source" nil)))

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
  (reset! cdt/CDT-DISPLAY-MSG display-background-msg)
  (set-control-thread))

(defmethod swank-eval :cdt [form]
           (cdt/safe-reval form true identity))

(defn get-full-stack-trace []
   (.getStackTrace (get-thread #_(.getName @control-thread)
                               (.name (cdt/ct)))))

(defmethod get-stack-trace :cdt [n]
           (println "gbj31" (type n) n)
           (cdt/scf n)
           (nth (get-full-stack-trace) n))

(defmethod exception-stacktrace :cdt [_]
           (println "gbj6")
           (map #(list %1 %2 '(:restartable nil))
                (iterate inc 0)
                (map str (get-full-stack-trace))))

(defmethod debugger-condition-for-emacs :cdt []
           (println "gbj5")
           core/*current-exception*)

(defn exception? [thrown-message]
  (.startsWith (first thrown-message) "CDT Exception"))

(defn get-quit-exception [thrown-message]
  (if (exception? thrown-message)
    core/debug-abort-exception
    core/debug-cdt-continue-exception))

(defmethod calculate-restarts :cdt [thrown-message]
           (let [quit-exception (get-quit-exception thrown-message)
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
  (cdt/scf n)
  (cdt/safe-reval (read-string string) true identity))

(defmacro make-cdt-method [name func]
  `(defmethod ~name :cdt []
              (println "gbj " '~func)
              (~(ns-resolve (the-ns 'com.georgejahad.cdt) func))
              true))

(make-cdt-method step step)
(make-cdt-method next step-over)
(make-cdt-method finish finish)
(make-cdt-method continue cont)

(defmethod show-source :cdt []
           (core/send-to-emacs '(:eval-no-wait "sldb-show-frame-source" (0))))

(backend-init)

