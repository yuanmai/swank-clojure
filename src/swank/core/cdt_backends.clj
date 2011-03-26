(ns swank.core.cdt-backends
  (:refer-clojure :exclude [next])
  (:require [cdt.core :as cdt]
            [swank.core.cdt-utils :as cutils]
            [swank.core :as core]
            [swank.util.concurrent.thread :as st])
  (:use swank.core.debugger-backends
        [swank.commands :only [defslimefn]]))


(defmethod swank-eval :cdt [form]
           (cdt/safe-reval (:thread @*debugger-env*)
                           (:frame @*debugger-env*) form true identity))

(defn- get-full-stack-trace []
   (.getStackTrace (cutils/get-thread #_(.getName @control-thread)
                               (.name (:thread @*debugger-env*)))))

(defmethod get-stack-trace :cdt [n]
           (swap! *debugger-env* assoc :frame n)
           (reset! last-viewed-source
                   (select-keys @*debugger-env* [:thread :frame]))
           (nth (get-full-stack-trace) n))

(defmethod exception-stacktrace :cdt [_]
           (map #(list %1 %2 '(:restartable nil))
                (iterate inc 0)
                (map str (get-full-stack-trace))))

(defmethod debugger-condition-for-emacs :cdt []
           (:env @*debugger-env*))

(defmethod calculate-restarts :cdt [_]
   (let [quit-exception (cutils/get-quit-exception)
         restarts
         [(core/make-restart :quit "QUIT"
                             "Quit to the SLIME top level"
                             #(throw cutils/debug-cdt-continue-exception))]
         restarts (core/add-restart-if
                   (pos? core/*sldb-level*)
                   restarts
                   :abort "ABORT" (str "ABORT to SLIME level "
                                       (dec core/*sldb-level*))
                   (fn [] (throw core/debug-abort-exception)))]
     (into (array-map) restarts)))

(defmethod build-backtrace :cdt [start end]
           (doall (take (- end start)
                        (drop start (exception-stacktrace nil)))))

(defmethod eval-string-in-frame :cdt [string n]
  (swap! *debugger-env* assoc :frame n)
  (cdt/safe-reval (:thread @*debugger-env*)
                   (:frame @*debugger-env*)
                   (read-string string) true identity))

(defmethod eval-last-frame :cdt [form-string]
           (cdt/safe-reval
            (:thread @last-viewed-source)
            (:frame @last-viewed-source)
            (read-string form-string) true identity))

(defn- reset-last-viewed-source []
  (reset! last-viewed-source (atom nil)))

(defmacro make-cdt-method [name func]
  `(defmethod ~name :cdt []
              (reset-last-viewed-source)
              (~(ns-resolve (the-ns 'cdt.core) func)
               (:thread @*debugger-env*))
              true))

(make-cdt-method step step)
(make-cdt-method next step-over)
(make-cdt-method finish finish)
(make-cdt-method continue continue-thread)

(defmethod line-bp :cdt [file line]
           (cdt/line-bp file line
                        (cutils/get-non-system-threads)
                        (cutils/get-system-thread-groups) true))

(defmacro set-bp [sym]
  `(cdt/set-bp-sym '~sym [(cutils/get-non-system-threads)
                           (cutils/get-system-thread-groups) true]))

(defmethod debugger-exception? :cdt [t]
           (or (cutils/debug-cdt-continue-exception? t)
               (cutils/debug-finish-exception? t)
               (cutils/debug-next-exception? t)
               (cutils/debug-step-exception? t)))

(defmethod handled-exception? :cdt [t]
  (cond
   (core/debug-continue-exception? t)
   true
   (cutils/debug-step-exception? t)
   (step)
   (cutils/debug-next-exception? t)
   (next)
   (cutils/debug-cdt-continue-exception? t)
   (continue)
   (cutils/debug-finish-exception? t)
   (finish)))

(defn- gen-debugger-env [env]
  (atom {:env (:env env)
         :thread (cdt/get-thread-from-id (:thread env))
         :frame 0}))

(defslimefn sldb-cdt-debug [env]
  (binding [*debugger-env* (gen-debugger-env env)]
    (core/sldb-debug nil nil core/*pending-continuations*)))

(defslimefn sldb-line-bp [file line]
  (line-bp file line))

(defslimefn sldb-step [_]
  (throw cutils/debug-step-exception))

(defslimefn sldb-next [_]
  (throw cutils/debug-next-exception))

(defslimefn sldb-out [_]
  (throw cutils/debug-finish-exception))

(defn set-catch [class]
           (cdt/set-catch class :all
                        (cutils/get-non-system-threads)
                        (cutils/get-system-thread-groups) true))

(defmethod handle-interrupt :cdt [_ _ _]
           (.deleteEventRequests
            (.eventRequestManager (cdt/vm))
            (.breakpointRequests (.eventRequestManager (cdt/vm))))
           (.deleteEventRequests
            (.eventRequestManager (cdt/vm))
            (.exceptionRequests (.eventRequestManager (cdt/vm))))
           (cdt/continue-vm)
           (reset! cdt/catch-list {})
           (reset! cdt/bp-list {})
           (reset-last-viewed-source)
           (println "Clearing CDT event requests and continuing"))

(def cdt-started (atom false))

(defn backend-init []
  (try
    (reset! cdt-started false)
    (reset! dispatch-val :cdt)
    (cdt/cdt-attach-pid)
    (cdt/set-handler cdt/exception-handler cutils/default-handler)
    (cdt/set-handler cdt/breakpoint-handler cutils/default-handler)
    (cdt/set-handler cdt/step-handler cutils/default-handler)
    (cdt/create-thread-start-request)
    (reset! cdt/CDT-DISPLAY-MSG cutils/display-background-msg)
    (cutils/set-control-thread)
    (cutils/set-system-thread-groups)
    (reset! cdt-started true)

  ;; this invocation of handle-interrupt is only needed to force the loading
  ;;  of the classes required by force-continue because inadvertently
  ;;  catching an exception which happens to be in the classloader can cause a
  ;;  deadlock

    (handle-interrupt :cdt nil nil)
    (catch Exception e
      (println "CDT startup failed")
      (reset! cdt-started e))))


(backend-init)

