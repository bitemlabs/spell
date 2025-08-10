(ns spell.core-test
  (:require [clojure.test :as t]
            [spell.core :as s]))
(t/use-fixtures :once (fn [f] (s/inst!) (f) (s/unst!)))

(t/deftest abbreviation-validation
  (t/is (s/valid? :int 42))
  (t/is (not (s/valid? :int "hi")))
  (t/is (s/valid? :string "hello"))
  (t/is (not (s/valid? :string 42))))

(s/df :a :int)
(s/df :b :int)

(t/deftest map-spec-validation
  (t/is (s/valid? {:req [:a :b]} {:a 1 :b 2}))
  (t/is (not (s/valid? {:req [:a :b]} {:a 1})))
  (t/is (s/valid? {:req [:a] :opt [:b]} {:a 1}))
  (t/is (s/valid? {:req [:a] :opt [:b]} {:a 1 :b 2}))
  (t/is (not (s/valid? {:req [:a] :opt [:b]} {:b 1}))))

(t/deftest logical-spec-validation
  (t/is (s/valid? [:or :int :string] 42))
  (t/is (s/valid? [:or :int :string] "hi"))
  (t/is (not (s/valid? [:or :int :string] :foo)))

  (t/is (s/valid? [:and int? #(>= % 0)] 3))
  (t/is (not (s/valid? [:and int? #(>= % 0)] -1))))

(t/deftest collection-spec-validation
  (t/is (s/valid? [:vector :int] [1 2 3]))
  (t/is (not (s/valid? [:vector :int] [1 "hi"])))
  (t/is (s/valid? [:list :string] '("a" "b")))
  (t/is (not (s/valid? [:list :string] '("a" 1))))
  (t/is (s/valid? [:set :keyword] #{:a :b}))
  (t/is (not (s/valid? [:set :keyword] #{:a 1}))))

(s/defnt square
  [x]
  [:int :=> :int]
  (* x x))

(t/deftest defnt-single-arity
  (t/is (= 9 (square 3)))
  (t/is (t/thrown? clojure.lang.ExceptionInfo (square "hi"))))

(s/defnt sum
  ([a] [:int :=> :int] a)
  ([a b] [:int :int :=> :int] (+ a b)))

(t/deftest defnt-multi-arity
  (t/is (= 5 (sum 5)))
  (t/is (= 7 (sum 3 4)))
  (t/is (t/thrown? clojure.lang.ExceptionInfo (sum 3 "x"))))

(t/deftest coerce-test
  (t/is (= 10 (s/coerce :int 10)))
  (t/is (t/thrown? clojure.lang.ExceptionInfo (s/coerce :int "bad"))))

(s/df :pos #(and (int? %) (pos? %)))

(t/deftest df-custom-spec-test
  (t/is (s/valid? :pos 3))
  (t/is (not (s/valid? :pos -1))))
