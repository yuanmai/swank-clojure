(ns swank.core.cdt-backends
  (:use swank.core.debugger-backends
        swank.core))

(defmethod exception-stacktrace :cdt [t]
#_  (map #(list %1 %2 '(:restartable nil))
       (iterate inc 0)
       (map str (.getStackTrace t)))
'	((0 "clojure.lang.Compiler.resolveIn(Compiler.java:5679)"
	    (:restartable nil))
	 (1 "clojure.lang.Compiler.resolve(Compiler.java:5623)"
	    (:restartable nil))
	 (2 "clojure.lang.Compiler.analyzeSymbol(Compiler.java:5586)"
	    (:restartable nil))
	 (3 "clojure.lang.Compiler.analyze(Compiler.java:5174)"
	    (:restartable nil))
	 (4 "clojure.lang.Compiler.analyze(Compiler.java:5153)"
	    (:restartable nil))
	 (5 "clojure.lang.Compiler$InvokeExpr.parse(Compiler.java:3036)"
	    (:restartable nil))
	 (6 "clojure.lang.Compiler.analyzeSeq(Compiler.java:5373)"
	    (:restartable nil))
	 (7 "clojure.lang.Compiler.analyze(Compiler.java:5192)"
	    (:restartable nil))
	 (8 "clojure.lang.Compiler.analyze(Compiler.java:5153)"
	    (:restartable nil))
	 (9 "clojure.lang.Compiler$BodyExpr$Parser.parse(Compiler.java:4670)"
	    (:restartable nil))))

(defmethod debugger-condition-for-emacs :cdt []
  (list "Testing cdt"
        "test2"
        nil))

(defmethod calculate-restarts :cdt [thrown]
  (let [restarts [(make-restart :quit "QUIT" "Quit gbj to the SLIME top level"
                               (fn [] (throw debug-quit-exception)))]
        restarts (add-restart-if
                  (pos? *sldb-level*)
                  restarts
                  :abort "ABORT" (str "ABORT to SLIME level " (dec *sldb-level*))
                  (fn [] (throw debug-abort-exception)))]
    (into (array-map) restarts)))

(defmethod build-backtrace :cdt [start end]
           (doall (take (- end start) (drop start (exception-stacktrace *current-exception*)))))

(defmethod eval-string-in-frame-internal :cdt [expr n]
  (if (and (zero? n) *current-env*)
    (with-bindings *current-env*
      (eval expr))))
