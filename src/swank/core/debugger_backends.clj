(ns swank.core.debugger-backends)

(def debugger-backend :cdt)
(def get-debugger-backend (constantly (var-get #'debugger-backend)))

(defmacro def-backend-multimethods [methods]
  `(do
     ~@(for [m methods]
        `(defmulti ~m get-debugger-backend))))

(def-backend-multimethods
  [exception-stacktrace debugger-condition-for-emacs calculate-restarts
   build-backtrace eval-string-in-frame-internal])
