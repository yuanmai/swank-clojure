(ns leiningen.jack-in
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

(defn jack-in
  "Jack in to a Clojure SLIME session.

This task is intended to be launched from Emacs using M-x clojure-jack-in,
which is part of the clojure-mode library."
  [project port]
  (println ";;; Bootstrapping bundled version of SLIME; please wait...\n\n")
  (println (string/join "\n" (payloads)))
  (println "(run-hooks 'slime-load-hook)")
  (swank project port "localhost" ":message" "\";;; proceed to jack in\""))
