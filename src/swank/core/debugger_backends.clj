(ns swank.core.debugger-backends
  (:refer-clojure :exclude [next])
  (:require [com.georgejahad.cdt :as cdt]))

(def #^{:dynamic true} *debugger-env* nil)

(defn get-debugger-backend [& args]
  (when *debugger-env* :cdt))

(defmacro def-backend-multimethods [methods]
  `(do
     ~@(for [m methods]
        `(defmulti ~m get-debugger-backend))))

(defmulti set-dbe-thread (fn [action _] action))

(defmulti line-bp (constantly :cdt))

(def-backend-multimethods
  [exception-stacktrace debugger-condition-for-emacs calculate-restarts
   build-backtrace eval-string-in-frame-internal step get-stack-trace
   show-source next finish continue swank-eval])
