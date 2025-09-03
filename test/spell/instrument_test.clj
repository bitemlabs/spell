(ns spell.instrument-test
   (:require
   [clojure.test :as t]
   [spell.instrument :as si]
   [spell.utils :as u]))

(t/use-fixtures
  :once
  (fn [f]
    (si/inst!)
    (f)))

(si/defnt square [x]
  [:int :=> :int]
  (* x x))

(t/deftest defnt-single-arity
  (binding [*ns* (-> #'square meta :ns)]
    (t/testing "pass"
      (t/is (= 9 (square 3))))
    (t/testing "fail"
      (let [err (atom nil)]
        (with-redefs [u/fail! (fn [_] (reset! err "!"))]
          (square 1.2)
          (t/is (= "!" @err)))))))

(si/defnt sum
  ([a] [:int :=> :int] a)
  ([a b] [:int :int :=> :int] (+ a b)))

(t/deftest defnt-multi-arity
  (binding [*ns* (-> #'sum meta :ns)]
    (t/testing "pass"
      (t/is (= 5 (sum 5)))
      (t/is (= 7 (sum 3 4))))
    (t/testing "fail"
      (let [err (atom nil)]
        (with-redefs [u/fail! (fn [_] (reset! err "!"))]
          (sum 3.2 4.3)
          (t/is (= "!" @err)))))))
