(ns spell.core-test
  (:require [clojure.test :refer :all]
            [spell.core :as s]))

(use-fixtures
  :once
  (fn [f]
    (s/inst!)
    (f)
    (s/unst!)))

(deftest abbreviation-validation
  (is (s/valid? :int 42))
  (is (not (s/valid? :int "hi")))
  (is (s/valid? :string "hello"))
  (is (not (s/valid? :string 42))))

(s/df :a :int)
(s/df :b :int)

(deftest map-spec-validation
  (is (s/valid? {:req [:a :b]} {:a 1 :b 2}))
  (is (not (s/valid? {:req [:a :b]} {:a 1})))
  (is (s/valid? {:req [:a] :opt [:b]} {:a 1}))
  (is (s/valid? {:req [:a] :opt [:b]} {:a 1 :b 2}))
  (is (not (s/valid? {:req [:a] :opt [:b]} {:b 1}))))

(deftest logical-spec-validation
  (is (s/valid? [:or :int :string] 42))
  (is (s/valid? [:or :int :string] "hi"))
  (is (not (s/valid? [:or :int :string] :foo)))

  (is (s/valid? [:and int? #(>= % 0)] 3))
  (is (not (s/valid? [:and int? #(>= % 0)] -1))))

(deftest collection-spec-validation
  (is (s/valid? [:vector :int] [1 2 3]))
  (is (not (s/valid? [:vector :int] [1 "hi"])))
  (is (s/valid? [:list :string] '("a" "b")))
  (is (not (s/valid? [:list :string] '("a" 1))))
  (is (s/valid? [:set :keyword] #{:a :b}))
  (is (not (s/valid? [:set :keyword] #{:a 1}))))

(s/defnt square
  [x]
  [:int :=> :int]
  (* x x))

(deftest defnt-single-arity
  (is (= 9 (square 3)))
  (is (thrown? clojure.lang.ExceptionInfo (square "hi"))))

(s/defnt sum
  ([a] [:int :=> :int] a)
  ([a b] [:int :int :=> :int] (+ a b)))

(deftest defnt-multi-arity
  (is (= 5 (sum 5)))
  (is (= 7 (sum 3 4)))
  (is (thrown? clojure.lang.ExceptionInfo (sum 3 "x"))))

(deftest coerce-test
  (is (= 10 (s/coerce :int 10)))
  (is (thrown? clojure.lang.ExceptionInfo (s/coerce :int "bad"))))

(s/df :pos #(and (int? %) (pos? %)))

(deftest df-custom-spec-test
  (is (s/valid? :pos 3))
  (is (not (s/valid? :pos -1))))
