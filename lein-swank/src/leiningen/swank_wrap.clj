(ns leiningen.swank-wrap
  (:require [leiningen.swank :as swank]
            [leiningen.run]))

(defn swank-wrap
  "Launch a swank server on the specified port, then run a -main function.

ALPHA: subject to change."
  [project port main & args]
  (swank/eval-in-project (update-in (swank/add-cdt-project-args project)
                                    [:dependencies] conj
                                    ['swank-clojure "1.4.0"])
                         `(do ~(swank/swank-form project port "localhost"
                                                 [":block" "false"])
                              ~((resolve 'leiningen.run/run-form) main args))
                        `(require '~(symbol main))))