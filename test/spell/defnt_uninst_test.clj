(ns spell.defnt-uninst-test
  (:require [clojure.test :as t]
            [spell.core :as s]))

(t/deftest defnt-executes-before-inst
  (s/unst!)
  (s/defnt foo [x] [:int :=> :int] (inc x))
  (t/is (= 2 (foo 1)))
  (s/inst!)
  (s/unst!))
