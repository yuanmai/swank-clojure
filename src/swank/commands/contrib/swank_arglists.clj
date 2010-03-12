(ns swank.commands.contrib.swank-arglists
  (:use (swank util core commands)))

((slime-fn 'swank-require) :swank-c-p-c)

(defslimefn arglist-for-echo-area [raw-specs & options]
  (let [{:keys [arg-indices
                print-right-margin
                print-lines]} (apply hash-map options)]
    ;; Yeah, I'm lazy -- I'll flesh this out later
    (if (and raw-specs
             (seq? raw-specs)
             (seq? (first raw-specs)))
      ((slime-fn 'operator-arglist) (ffirst raw-specs) *current-package*)
      nil)))

(defslimefn autodoc
  "Return a string representing the arglist for the deepest subform in
RAW-FORM that does have an arglist. The highlighted parameter is
wrapped in ===> X <===."
  [raw-specs & options]
  (let [{:keys [print-right-margin
                print-lines]} (if (first options)
                                (apply hash-map options)
                                {})]
    (if (and raw-specs
             (seq? raw-specs))
      (let [expr (some #(and (seq? %) (some #{:cursor-marker} %) %)
                       (tree-seq seq? seq raw-specs))]
        (if (and (seq? expr) (not (= (first expr) "")))
          ((slime-fn 'operator-arglist)
           (first expr)
           *current-package*)
          `:not-available))
      `:not-available)))

(defslimefn variable-desc-for-echo-area [variable-name]
  (with-emacs-package
   (or
    (try
     (when-let [sym (read-string variable-name)]
       (when-let [var (resolve sym)]
         (when (.isBound #^clojure.lang.Var var)
           (str variable-name " => " (var-get var)))))
     (catch Exception e nil))
    "")))
