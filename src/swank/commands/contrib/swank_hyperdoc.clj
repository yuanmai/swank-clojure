(ns swank.commands.contrib.swank-hyperdoc
  (:use (swank util core commands)))

(def package-pages
     { 'clojure.core "http://richhickey.github.com/clojure/clojure.core-api.html#" })

(defn hyperdoc-lookup [string]
  (with-emacs-package
    (when-let [ns-found
               (some #(and (ns-resolve (first %) (symbol string)) (first %))
                     package-pages)]
      (list (list ns-found '. (str (package-pages ns-found) ns-found "/" string))))))

(defslimefn hyperdoc [string]
  (hyperdoc-lookup string))
