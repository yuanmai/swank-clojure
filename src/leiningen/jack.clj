(ns leiningen.jack
  (:use [leiningen.compile :only [eval-in-project]]
        [leiningen.swank :only [swank]])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn elisp-payload-files []
  ;; hard-coded in for now
  ["swank/payload/slime.el" "swank/payload/slime-repl.el"])

(defn payloads []
  (for [file (elisp-payload-files)]
    (slurp (io/resource file))))

(defn ^{:help-arglists '([project] [project port] [project port host & opts])}
  jack [project port]
  (println (string/join "\n" (payloads)))
  (swank project port "localhost" ":message" "\";;; proceed to jack in\""))
