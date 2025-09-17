(ns spell.instrument-test
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [spell.core :as s]
   [spell.instrument :as si :refer [tdefn tlet]]
   [spell.utils :as u])
  #?(:cljs (:require-macros [spell.instrument :as si])))

;; Ensure each test runs with instrumentation ON (high) unless overridden.
(t/use-fixtures
  :each
  (fn [f]
    (binding [*ns* 'spell.instrument-test]
      (si/inst!)
      (f)
      (si/unst!))))

;; ---------------------------------------------------------------------------
;; Definitions (qualified first, then alternatives)
;; ---------------------------------------------------------------------------

;; Qualified (recommended, clojure.spec-style)
(s/define :my.app/int int?)
(s/define :my.app/string string?)
(s/define :my.app/keyword keyword?)

;; Alternatives for convenience
(s/define :int int?)
(s/define :string string?)
(s/define ["age"] pos-int?)
(s/define "age" :string)
(s/define [:coords] [:vector int?])

;; Map field specs
(s/define :my.app/a :my.app/int)
(s/define :my.app/b :my.app/int)
(s/define :person/name :my.app/string)

;; ---------------------------------------------------------------------------
;; tdefn examples (qualified signatures first)
;; ---------------------------------------------------------------------------

(tdefn square [x]
  [:my.app/int :=> :my.app/int]
  (* x x))

(tdefn sum
  ([a]   [:my.app/int :=> :my.app/int] a)
  ([a b] [:my.app/int :my.app/int :=> :my.app/int] (+ a b)))

;; Alternative forms in signatures
(tdefn hypotenuse-len
  [[x y]]
  [[:vector :int] :=> :my.app/int]
  (let [xx (* x x)
        yy (* y y)]
    ;; produce an int for testing purposes (no floating sqrt to avoid platform diffs)
    (+ xx yy)))

(tdefn stringify-age [n]
  ["age" :=> :my.app/string]
  (str n))

;; ---------------------------------------------------------------------------
;; tdefn tests
;; ---------------------------------------------------------------------------

(t/deftest tdefn-single-arity
  (t/testing "pass (qualified)"
    (t/is (= 9 (square 3))))
  (t/testing "fail (qualified)"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_] (reset! err "!"))]
        (square 1.2)
        (t/is (= "!" @err))))))

(t/deftest tdefn-multi-arity
  (t/testing "pass (qualified)"
    (t/is (= 5 (sum 5)))
    (t/is (= 7 (sum 3 4))))
  (t/testing "fail (qualified)"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_] (reset! err "!"))]
        (sum 3.2 4.3)
        (t/is (= "!" @err))))))

(t/deftest tdefn-alternative-signatures
  (t/testing "vector-identified spec in signature"
    (t/is (= 25 (hypotenuse-len [3 4]))))
  (t/testing "failure paths for alt forms"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_] (reset! err "!"))]
        (reset! err nil)
        (stringify-age -5)
        (t/is (= "!" @err))))))

;; ---------------------------------------------------------------------------
;; tlet tests (qualified first, then show alternatives)
;; ---------------------------------------------------------------------------

(t/deftest tlet-simple-symbol-binding
  (t/is (= 9 (tlet [x :my.app/int 9] x)))
  (t/is (= 7 (tlet [a :my.app/int 3
                    b :my.app/int 4]
               (+ a b)))))

(t/deftest tlet-vector-destructuring
  (t/is (= 3 (tlet [[a b c] [:vector :my.app/int] [1 1 1]]
                   (+ a b c)))))

(t/deftest tlet-map-destructuring
  (t/is (= 3 (tlet [{:keys [:my.app/a :my.app/b]} {:req [:my.app/a :my.app/b]}
                    {:my.app/a 1 :my.app/b 2}]
               (+ a b)))))

(t/deftest tlet-logical-specs
  (t/is (= "5" (tlet [x [:or :my.app/string :my.app/int] "5"] x)))
  (t/is (= 12 (tlet [x [:and int? #(<= 0 % 100)] 12] x))))

(t/deftest tlet-collection-specs
  (t/is (= 3 (tlet [xs [:vector :my.app/int] [1 2 3]]
                   (count xs))))
  (t/is (= 2 (tlet [ks [:set :my.app/keyword] #{:a :b}]
                   (count ks)))))

(t/deftest tlet-alternative-forms
  (t/testing "string identifier"
    (t/is (= 20 (tlet [age ["age"] 20] age))))
  (t/testing "vector identifier"
    (t/is (= 2 (tlet [xy [:coords] [10 20]] (count xy)))))

  (t/testing "unqualified keywords"
    (t/is (= 8 (tlet [x :int 3
                      y :int (+ x 5)]
                     y)))))

(t/deftest tlet-nesting
  (t/is (= 6 (tlet [x :my.app/int 2]
                   (tlet [y :my.app/int 3]
                         (+ x y 1))))))

(t/deftest tlet-none-mode-never-checks
  (si/unst!)
  (t/is (= "ok" (tlet [_x :my.app/int "nope"] "ok"))))

(t/deftest tlet-destructuring-with-extra-keys
  (t/is (= 3 (tlet [{:keys [:my.app/a :my.app/b]} {:req [:my.app/a :my.app/b]}
                    {:my.app/a 1 :my.app/b 2 :c 3}]
                   (+ a b)))))

(t/deftest tlet-binding-vector-arity-errors
  (let [err (atom nil)]
    (with-redefs [u/fail! (fn [_] (reset! err "!"))]
      (t/testing "single binding (missing a part)"
        (reset! err nil)
        (tlet [x :my.app/int "1"] x)
        (t/is (= "!" @err)))
      (t/testing "multiple bindings (one malformed)"
        (reset! err nil)
        (tlet [x :my.app/int 1 _y :my.app/int "2"] x)
        (t/is (= "!" @err))))))

;; ---------------------------------------------------------------------------
;; Mixed end-to-end examples combining forms
;; ---------------------------------------------------------------------------

(t/deftest mixed-examples
  (t/is (s/valid? :person/name "alice\""))
  (t/is (s/valid? [:or :my.app/int :my.app/string] 1))
  (t/is (s/valid? [:and :my.app/int #(< % 18)] 12))
  (t/is (not (s/valid? [:and :my.app/int #(< % 18)] 89)))
  (t/is (s/valid? {:req [:person/name]} {:person/name "alice\""}))
  (t/is (not (s/valid? {:req [:person/name]} {:person/name 123}))))
