(ns spell.core-test
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [spell.core :as s]
   [spell.utils :as u]))

(t/deftest abbreviation-validation
  (t/is (s/valid? :int 42))
  (t/is (not (s/valid? :int "hi")))
  (t/is (s/valid? :string "hello"))
  (t/is (not (s/valid? :string 42)))
  (t/is (s/valid? :keyword :foo))
  (t/is (not (s/valid? :keyword "foo"))))

(s/def :a :int)
(s/def :b :int)

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

(t/deftest coerce-test
  (t/testing "pass"
    (t/is (= 10 (s/coerce :int 10))))
  (t/testing "fail"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_m] (reset! err "!"))]
        (s/coerce :int "bad")
        (t/is (= "!" @err))))))

(s/def :pos
  #(and (int? %) (pos? %)))

(t/deftest df-custom-spec-test
  (t/testing "pass"
    (t/is (s/valid? :pos 3)))
  (t/testing "fail"
    (t/is (not (s/valid? :pos -1)))))

(t/deftest predefs-expanded-test
  (let [samples (merge
                 {:integer  [1 1.5]
                  :symbol   ['sym :sym]
                  :boolean  [true 0]
                  :float    [(float 1.0) 1.0]
                  :double   [1.0 1]
                  :number   [1 "1"]
                  :uuid     [#?(:clj (java.util.UUID/randomUUID)
                                  :cljs (random-uuid)) "uuid"]
                  :char     [\a "a"]
                  :fn       [(fn []) 1]
                  :map      [{} []]
                  :vector   [[] {}]
                  :set      [#{} []]
                  :list     ['() []]
                  :seq      [(seq [1]) 1]
                  :coll     [[] 1]
                  :seqable  [[] 1]
                  :sequential [[1 2] #{1}]
                  :empty    [[] [1]]
                  :some     [1 nil]
                  :nil      [nil 1]
                  :even     [2 1]
                  :odd      [1 2]
                  :pos      [1 0]
                  :neg      [-1 0]
                  :zero     [0 1]
                  :pos-int  [1 0]
                  :neg-int  [-1 1]
                  :nat-int  [0 -1]
                  :rational [1 #?(:clj Double/NaN :cljs js/NaN)]}
                 #?(:clj {:ratio   [1/2 0.5]
                          :decimal [(bigdec 1) 1.0]}))]
    (doseq [[kw [good bad]] samples]
      (t/testing (str kw)
        (t/is (s/valid? kw good))
        (t/is (not (s/valid? kw bad)))))
    (t/testing ":any accepts everything"
      (t/is (s/valid? :any nil))
      (t/is (s/valid? :any 42)))))

(t/deftest predef-vs-userdef-test
  ;; user-defined spec for :string should not override built-in
  (s/def :string (constantly false))
  (s/def :life #(= 42 %))
  (t/testing "predef wins"
    (t/is (s/valid? :string "hi"))
    (t/is (not (s/valid? :string 1))))
  (t/testing "user-defined used when no predef"
    (t/is (s/valid? :life 42))
    (t/is (not (s/valid? :life 0)))))
