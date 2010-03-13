(ns swank.commands.contrib.swank-autodoc
  (:use (swank util core commands)
        [swank.commands.basic :only [operator-arglist]]))

(defn autodoc*
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

(defslimefn autodoc
  "Return a string representing the arglist for the deepest subform in
RAW-FORM that does have an arglist.
TODO: The highlighted parameter is wrapped in ===> X <===."
  [raw-specs & options]
  (apply autodoc* raw-specs options))

