(ns swank.core.cdt-backends
  (:use swank.core.debugger-backends
        swank.core))

(defmethod exception-stacktrace :cdt [t]
  (map #(list %1 %2 '(:restartable nil))
       (iterate inc 0)
       (map str (.getStackTrace t))))

(defmethod debugger-condition-for-emacs :cdt []
  (list (or (.getMessage *current-exception*) "No message.")
        (str "  [Thrown " (class *current-exception*) "]")
        nil))

(defmethod calculate-restarts :cdt [thrown]
  (let [restarts [(make-restart :quit "QUIT" "Quit gbj to the SLIME top level"
                               (fn [] (throw debug-quit-exception)))]
        restarts (add-restart-if
                  (pos? *sldb-level*)
                  restarts
                  :abort "ABORT" (str "ABORT to SLIME level " (dec *sldb-level*))
                  (fn [] (throw debug-abort-exception)))
        restarts (add-restart-if
                  (and (.getMessage thrown)
                       (.contains (.getMessage thrown) "BREAK"))
                  restarts
                  :continue "CONTINUE" (str "Continue from breakpoint")
                  (fn [] (throw debug-continue-exception)))
        restarts (add-cause-restarts restarts thrown)]
    (into (array-map) restarts)))

(defmethod build-backtrace :cdt [start end]
           (doall (take (- end start) (drop start (exception-stacktrace *current-exception*)))))

(defmethod eval-string-in-frame-internal :cdt [expr n]
  (if (and (zero? n) *current-env*)
    (with-bindings *current-env*
      (eval expr))))
