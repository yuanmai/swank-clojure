(ns swank.core.debugger-backends
  (:refer-clojure :exclude [next]))

(def #^{:dynamic true} *debugger-env* nil)

(defn get-debugger-backend [& args]
  (when *debugger-env* :cdt))

(def dispatch-val (atom :default))

(defn dbe-dispatch [& args]
  @dispatch-val)

(defmacro def-backend-multimethods [methods]
  `(do
     ~@(for [m methods]
        `(defmulti ~m get-debugger-backend))))

(def-backend-multimethods
  [exception-stacktrace debugger-condition-for-emacs calculate-restarts
   build-backtrace eval-string-in-frame step get-stack-trace
   next finish continue swank-eval handled-exception? debugger-exception?])

(defmulti line-bp dbe-dispatch)
(defmulti force-continue dbe-dispatch)

