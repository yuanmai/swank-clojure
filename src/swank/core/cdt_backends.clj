(ns swank.core.cdt-backends
  (:refer-clojure :exclude [next])
  (:require [cdt.core :as cdtc]
            [cdt.utils :as cdtu]
            [cdt.events :as cdte]
            [cdt.reval :as cdtr]
            [cdt.break :as cdtb]
            [swank.core.cdt-utils :as cutils]
            [swank.core :as core]
            [swank.util.concurrent.thread :as st])
  (:use swank.core.debugger-backends
        [swank.commands :only [defslimefn]]))


(defmethod swank-eval :cdt [form]
           (cdtr/safe-reval (:thread *debugger-env*)
                           @(:frame *debugger-env*) form true identity))

(defn- get-full-stack-trace []
   (.getStackTrace (cutils/get-thread #_(.getName @control-thread)
                               (.name (:thread *debugger-env*)))))

(defmethod get-stack-trace :cdt [n]
           (reset! (:frame *debugger-env*) n)
           (nth (get-full-stack-trace) n))

(defmethod exception-stacktrace :cdt [_]
           (map #(list %1 %2 '(:restartable nil))
                (iterate inc 0)
                (map str (get-full-stack-trace))))

(defmethod debugger-condition-for-emacs :cdt []
           (:env *debugger-env*))

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
  (reset! (:frame *debugger-env*) n)
  (cdtr/safe-reval (:thread *debugger-env*)
                  @(:frame *debugger-env*)
                  (read-string string) true identity))

(defmacro make-cdt-method [name func]
  `(defmethod ~name :cdt []
              (~(ns-resolve (the-ns 'cdt.events) func)
               (:thread *debugger-env*))
              true))

(make-cdt-method step step)
(make-cdt-method next step-over)
(make-cdt-method finish finish)
(make-cdt-method continue continue-thread)

(defmethod line-bp :cdt [file line]
           (cdtb/line-bp file line
                        (cutils/get-non-system-threads)
                        (cutils/get-system-thread-groups) true))

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
  {:env (:env env)
   :thread (cdte/get-thread-from-id (:thread env))
   :frame (atom 0)})

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
           (cdte/set-catch class :all
                        (cutils/get-non-system-threads)
                        (cutils/get-system-thread-groups) true))

(defmethod force-continue :cdt []
           (.deleteEventRequests
            (.eventRequestManager (cdtu/vm))
            (.breakpointRequests (.eventRequestManager (cdtu/vm))))
           (.deleteEventRequests
            (.eventRequestManager (cdtu/vm))
            (.exceptionRequests (.eventRequestManager (cdtu/vm))))
           (cdtu/continue-vm)
           (reset! cdte/catch-list nil)
           (reset! cdte/bp-list nil))

(defn backend-init []
  (reset! dispatch-val :cdt)
  (cdtc/cdt-attach-pid)
  (cdte/set-handler cdte/exception-handler cutils/default-handler)
  (cdte/set-handler cdte/breakpoint-handler cutils/default-handler)
  (cdte/set-handler cdte/step-handler cutils/default-handler)
  (cdte/create-thread-start-request)
  (reset! cdtu/CDT-DISPLAY-MSG cutils/display-background-msg)
  (cutils/set-control-thread)
  (cutils/set-system-thread-groups)

  ;; this invocation of force-continue is only needed to force the loading
  ;;  of the classes required by force-continue because inadvertently
  ;;  catching an exception which happens to be in the classloader can cause a
  ;;  deadlock

  (force-continue))


(backend-init)

