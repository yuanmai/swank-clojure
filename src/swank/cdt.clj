(ns swank.cdt
  ;; convenience namespace to give users easy access to main functions
  (:refer-clojure :exclude [next])
  (:require [cdt.ui :as cdt]
            [swank.core.cdt-utils :as cutils]
            [swank.core.cdt-backends :as cbackends]))

(cdt/expose cbackends/set-catch cbackends/set-bp cbackends/reval
            cdt/delete-catch cdt/delete-bp cdt/delete-all-breakpoints
            cdt/print-bps cdt/bg)
