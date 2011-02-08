(ns swank.core.cdt-backends
  (:require [com.georgejahad.cdt :as cdt]
            [swank.util.concurrent.mbox :as mb]
            [swank.core :as core])
  (:use swank.core.debugger-backends))

(defn match-name [thread-name]
  #(re-find (re-pattern thread-name) (.getName %)))

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

(defn default-handler [e]
  (when-not @control-thread
    (set-control-thread))
  (mb/send @control-thread
           '(:cdt-rex "(sldb-cdt-debug)" :cdt-thread)))

(defn backend-init []
  (cdt/set-handler cdt/exception-handler default-handler)
  (cdt/set-handler cdt/breakpoint-handler default-handler)
  (cdt/set-handler cdt/step-handler default-handler))

(defmethod get-stack-trace :cdt []
           (.getStackTrace (get-thread #_(.getName @control-thread)  (.name (cdt/ct)))))

(defmethod exception-stacktrace :cdt [_]
           (println "gbj6")
           (map #(list %1 %2 '(:restartable nil))
                (iterate inc 0)
                (map str (get-stack-trace))))



(defmethod debugger-condition-for-emacs :cdt []
           (println "gbj5")
           (list "CDT Event"
                 "test2"
                 nil))

(defmethod calculate-restarts :cdt [thrown]
  (let [restarts [(core/make-restart :quit "QUIT" "Quit gbj to the SLIME top level"
                                     (fn [] (throw core/debug-quit-exception)))]
        restarts (conj restarts
                       (core/make-restart :step "STEP" "Step"
                                          (fn [] (throw core/debug-step-exception))))
        restarts (core/add-restart-if
                  (pos? core/*sldb-level*)
                  restarts
                  :abort "ABORT" (str "ABORT to SLIME level " (dec core/*sldb-level*))
                  (fn [] (throw core/debug-abort-exception)))]
    (into (array-map) restarts)))

(defmethod build-backtrace :cdt [start end]
           (doall (take (- end start) (drop start (exception-stacktrace core/*current-exception*)))))

(defmethod eval-string-in-frame-internal :cdt [string n]
  (cdt/scf n)
  (cdt/safe-reval (read-string string) true))

(defmethod step :cdt []
     (println "gbj11")
           (cdt/step))

(backend-init)

