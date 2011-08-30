(ns leiningen.jack-in
  (:use [leiningen.compile :only [eval-in-project]]
        [leiningen.swank :only [swank]])
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.util.jar JarFile)
           (java.security MessageDigest)))

(defn- get-manifest [file]
  (let [attrs (-> file JarFile. .getManifest .getMainAttributes)]
    (zipmap (map str (keys attrs)) (vals attrs))))

(defn- get-payloads [file]
  (.split (get (get-manifest file) "Swank-Elisp-Payload") " "))

(defn elisp-payload-files []
  ["swank/payload/slime.el" "swank/payload/slime-repl.el"]
  #_(apply concat ["swank/payload/slime.el" "swank/payload/slime-repl.el"]
           (->> (scan-paths (System/getProperty "sun.boot.class.path")
                            (System/getProperty "java.ext.dirs")
                            (System/getProperty "java.class.path"))
                (filter #(jar-file? (.getName (:file %))))
                (get-payloads))))

(defn hex-digest [file]
  (format "%x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA1")
                                       (-> file io/resource slurp .getBytes)))))

(defn loader [file]
  (let [feature (second (re-find #".*/(.*?).el$" file))
        checksum (subs (hex-digest file) 0 8)
        user-file (format "%s/.emacs.d/swank/%s-%s.elc"
                          (System/getProperty "user.home")
                          feature checksum)]
    (.mkdirs (.getParentFile (io/file user-file)))
    (io/copy (.openStream (io/resource file)) (io/file user-file))
    (with-open [w (io/writer user-file :append true)]
      (.write w (format "\n(provide '%s-%s)\n" feature checksum)))
    (format "(when (not (featurep '%s-%s))
               (if (file-readable-p \"%s\")
                 (load-file \"%s\")
               (byte-compile-file \"%s\" t)))"
            feature checksum user-file user-file
            (.replaceAll user-file "\\.elc$" ".el"))))

(defn payload-loaders []
  (for [file (elisp-payload-files)]
    (loader file)))

(defn jack-in
  "Jack in to a Clojure SLIME session from Emacs.

This task is intended to be launched from Emacs using M-x clojure-jack-in,
which is part of the clojure-mode library."
  [project port]
  (println ";;; Bootstrapping bundled version of SLIME; please wait...\n\n")
  (println (string/join "\n" (payload-loaders)))
  (println "(sleep-for 0.1)") ; TODO: remove
  (println "(run-hooks 'slime-load-hook) ; on port" port)
  (swank project port "localhost" ":message" "\";;; proceed to jack in\""))
