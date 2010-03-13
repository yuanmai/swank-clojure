(ns swank.test-swank.commands.contrib.swank-hyperdoc
  (:use swank.commands.contrib.swank-hyperdoc :reload-all)
  (:use clojure.test))

(defn emacs-package-fixture [f]
  (binding [swank.core/*current-package* "user"]
    (f)))

(use-fixtures :each emacs-package-fixture)

(deftest hyperdoc-test
  (testing "hyperdoc for symbol"
    (is (= '((clojure.core . "http://richhickey.github.com/clojure/clojure.core-api.html#clojure.core/list")) (hyperdoc-lookup "list"))))
    (testing "hyperdoc for invalid symbol"
    (is (nil? (hyperdoc-lookup "list1234")))))
