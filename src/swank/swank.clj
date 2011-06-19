;;;; swank-clojure.clj --- Swank server for Clojure
;;;
;;; Copyright (C) 2008 Jeffrey Chu
;;;
;;; This file is licensed under the terms of the GNU General Public
;;; License as distributed with Emacs (press C-h C-c to view it).
;;;
;;; See README file for more information about installation
;;;

(ns swank.swank
  (:use [swank.core]
        [swank.core connection server]
        [swank.util.concurrent thread]
        [swank.util.net sockets]
        [clojure.main :only [repl]])
  (:require [swank.commands]
            [swank.commands basic indent completion
             contrib inspector])
  (:import [java.lang System]
           [java.io File])
  (:gen-class))

(defn ignore-protocol-version [version]
  (reset! protocol-version version))

(defn- connection-serve [conn]
  (let [control
        (dothread-swank
          (thread-set-name "Swank Control Thread")
          (try
           (control-loop conn)
           (catch Exception e
             (.println System/err "exception in control loop")
             (.printStackTrace e)
             nil))
          (close-socket! (conn :socket)))
        read
        (dothread-swank
          (thread-set-name "Read Loop Thread")
          (try
           (read-loop conn control)
           (catch Exception e
             ;; This could be put somewhere better
             (.println System/err "exception in read loop")
             (.printStackTrace e)
             (.interrupt control)
             (dosync (alter connections (partial remove #{conn}))))))]
    (dosync
     (ref-set (conn :control-thread) control)
     (ref-set (conn :read-thread) read))))

(defn load-cdt-with-dynamic-classloader []
    ;; cdt requires a dynamic classloader for tools.jar add-classpath
    ;;  lein swank doesn't seem to provide one.  Loading the backend
    ;;  like this works around that problem.
    (.start (Thread. #(do (use 'swank.core.cdt-backends)
                          (eval '(cdt-backend-init))))))

(defn start-server
  "Start the server. This is the entry point for Emacs."
  [& opts]
  (let [opts (apply hash-map opts)]
    (when (:load-cdt-on-startup opts)
      (load-cdt-with-dynamic-classloader))
    (setup-server (get opts :port 0)
                  simple-announce
                  connection-serve
                  opts)))

(defn start-repl
  "Start the server wrapped in a repl. Use this to embed swank in your code."
  ([port & opts]
     (let [stop (atom false)
           opts (assoc (apply hash-map opts) :port (Integer. port))]
       (repl :read (fn [rprompt rexit]
                     (if @stop rexit
                         (do (reset! stop true)
                             `(start-server ~@(apply concat opts)))))
             :need-prompt (constantly false))))
  ([] (start-repl 4005)))

(def -main start-repl)
