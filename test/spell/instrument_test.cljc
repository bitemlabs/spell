(ns spell.instrument-test
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [spell.core :as s]
   [spell.instrument :as si :refer [defnt tlet]]
   [spell.utils :as u])
  #?(:cljs (:require-macros [spell.instrument :as si])))

(t/use-fixtures
  :each
  (fn [f]
    (binding [*ns* 'spell.instrument-test]
      (si/inst!)
      (f)
      (si/unst!))))

(defnt square [x]
  [:int :=> :int]
  (* x x))

(defnt sum
  ([a] [:int :=> :int] a)
  ([a b] [:int :int :=> :int] (+ a b)))

(s/def :a :int)
(s/def :b :int)
(s/def ["age"] pos-int?)

(t/deftest defnt-single-arity
  (t/testing "pass"
    (t/is (= 9 (square 3))))
  (t/testing "fail"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_] (reset! err "!"))]
        (square 1.2)
        (t/is (= "!" @err))))))

(t/deftest defnt-multi-arity
  (t/testing "pass"
    (t/is (= 5 (sum 5)))
    (t/is (= 7 (sum 3 4))))
  (t/testing "fail"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_] (reset! err "!"))]
        (sum 3.2 4.3)
        (t/is (= "!" @err))))))

(t/deftest tlet-simple-symbol-binding
  (t/is (= 9 (tlet [x :int 9] x)))
  (t/is (= 7 (tlet [a :int 3
                    b :int 4]
                   (+ a b)))))

(t/deftest tlet-vector-destructuring
  (t/is (= 3 (tlet [[a b c] [:vector :int] [1 1 1]]
                   (+ a b c)))))

(t/deftest tlet-map-destructuring
  (t/is (= 3 (tlet [{:keys [a b]} {:req [:a :b]} {:a 1 :b 2}]
               (+ a b)))))

(t/deftest tlet-logical-specs
  (t/is (= "5" (tlet [x [:or :string :int] "5"] x)))
  (t/is (= 12 (tlet [x [:and int? #(<= 0 % 100)] 12] x))))

(t/deftest tlet-collection-specs
  (t/is (= 3 (tlet [xs [:vector :int] [1 2 3]]
                   (count xs))))
  (t/is (= 2 (tlet [ks [:set :keyword] #{:a :b}]
                   (count ks)))))

(t/deftest tlet-string-key-spec-name
  (t/is (= 20 (tlet [age ["age"] 20] age))))

(t/deftest tlet-uses-earlier-bindings-like-let
  (t/is (= 8 (tlet [x :int 3
                    y :int (+ x 5)]
                   y))))

(t/deftest tlet-nesting
  (t/is (= 6 (tlet [x :int 2]
                   (tlet [y :int 3]
                         (+ x y 1))))))

(t/deftest tlet-none-mode-never-checks
  (si/unst!)
  (t/is (= "ok" (tlet [_x :int "nope"] "ok"))))

(t/deftest tlet-destructuring-with-extra-keys
  (t/is (= 3 (tlet [{:keys [a b]} {:req [:a :b]} {:a 1 :b 2 :c 3}]
                   (+ a b)))))

(t/deftest tlet-binding-vector-arity-errors
  (let [err (atom nil)]
    (with-redefs [u/fail! (fn [_] (reset! err "!"))]
      (t/testing "single binding"
        (reset! err nil)
        (tlet [x :int "1"] x)
        (t/is (= "!" @err)))
      (t/testing "multiple bindings"
        (reset! err nil)
        (tlet [x :int 1 _y :int "2"] x)
        (t/is (= "!" @err))))))
