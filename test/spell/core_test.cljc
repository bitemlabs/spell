(ns spell.core-test
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [spell.core :as s]
   [spell.utils :as u]))

;; ---------------------------------------------------------------------------
;; Basic validation (qualified keywords preferred)
;; ---------------------------------------------------------------------------

(s/define :my.app/int int?)
(s/define :my.app/string string?)
(s/define :my.app/keyword keyword?)

(t/deftest abbreviation-validation
  (t/testing "qualified keywords"
    (t/is (s/valid? :my.app/int 42))
    (t/is (not (s/valid? :my.app/int "hi")))
    (t/is (s/valid? :my.app/string "hello"))
    (t/is (not (s/valid? :my.app/string 42)))
    (t/is (s/valid? :my.app/keyword :foo))
    (t/is (not (s/valid? :my.app/keyword "foo"))))
  (t/testing "unqualified keywords"
    (s/define :int int?)
    (s/define :string string?)
    (t/is (s/valid? :int 99))
    (t/is (not (s/valid? :string 42))))
  (t/testing "string identifiers"
    (s/define ["age"] pos-int?)
    (t/is (s/valid? ["age"] 30))
    (t/is (not (s/valid? ["age"] -1))))
  (t/testing "vector identifiers"
    (s/define [:coords] [:vector int?])
    (t/is (s/valid? [:coords] [1 2 3]))
    (t/is (not (s/valid? [:coords] [1 "oops"])))))

;; ---------------------------------------------------------------------------
;; Map validation
;; ---------------------------------------------------------------------------

(s/define :my.app/a :int)
(s/define :my.app/b :int)

(t/deftest map-spec-validation
  (t/testing "qualified maps"
    (t/is (s/valid? {:req [:my.app/a :my.app/b]}
                    {:my.app/a 1 :my.app/b 2}))
    (t/is (not (s/valid? {:req [:my.app/a :my.app/b]}
                         {:my.app/a 1})))
    (t/is (s/valid? {:req [:my.app/a] :opt [:my.app/b]}
                    {:my.app/a 1}))
    (t/is (s/valid? {:req [:my.app/a] :opt [:my.app/b]}
                    {:my.app/a 1 :my.app/b 2}))
    (t/is (not (s/valid? {:req [:my.app/a] :opt [:my.app/b]}
                         {:my.app/a 1 :my.app/b "2"})))
    (t/is (s/valid? {:req [:my.app/a] :opt [:my.app/b]}
                    {:my.app/a 1 :my.app/b 2}))
    (t/is (not (s/valid? {:req [:my.app/a] :opt [:my.app/b]}
                         {:my.app/b 1}))))
  (t/testing "unqualified maps"
    (s/define :a :int)
    (s/define :b :int)
    (t/is (s/valid? {:req [:a :b]} {:a 1 :b 2}))))

;; ---------------------------------------------------------------------------
;; Logical operators
;; ---------------------------------------------------------------------------

(t/deftest logical-spec-validation
  (t/testing "qualified"
    (t/is (s/valid? [:or :my.app/int :my.app/string] 42))
    (t/is (s/valid? [:or :my.app/int :my.app/string] "hi"))
    (t/is (not (s/valid? [:or :my.app/int :my.app/string] :foo))))
  (t/testing "mixed forms"
    (t/is (s/valid? [:and int? #(>= % 0)] 3))
    (t/is (not (s/valid? [:and int? #(>= % 0)] -1)))
    (t/is (s/valid? [:enum 1 2 3] 3))
    (t/is (not (s/valid? [:enum "abc" 123 false] -1)))))

;; ---------------------------------------------------------------------------
;; Collections
;; ---------------------------------------------------------------------------

(t/deftest collection-spec-validation
  (t/testing "qualified"
    (t/is (s/valid? [:vector :my.app/int] [1 2 3]))
    (t/is (not (s/valid? [:vector :my.app/int] [1 "hi"])))
    (t/is (s/valid? [:list :my.app/string] '("a" "b")))
    (t/is (not (s/valid? [:list :my.app/string] '("a" 1))))
    (t/is (s/valid? [:set :my.app/keyword] #{:a :b}))
    (t/is (not (s/valid? [:set :my.app/keyword] #{:a 1}))))
  (t/testing "alternative forms"
    (t/is (s/valid? [:vector :int] [9 10]))))

;; ---------------------------------------------------------------------------
;; Coercion
;; ---------------------------------------------------------------------------

(t/deftest coerce-test
  (t/testing "pass"
    (t/is (= 10 (s/coerce! :my.app/int 10))))
  (t/testing "fail"
    (let [err (atom nil)]
      (with-redefs [u/fail! (fn [_m] (reset! err "!"))]
        (s/coerce! :my.app/int "bad")
        (t/is (= "!" @err))))))

;; ---------------------------------------------------------------------------
;; Custom spec
;; ---------------------------------------------------------------------------

(s/define :my.app/pos
  #(and (int? %) (pos? %)))

(t/deftest df-custom-spec-test
  (t/testing "pass"
    (t/is (s/valid? :my.app/pos 3)))
  (t/testing "fail"
    (t/is (not (s/valid? :my.app/pos -1)))))

;; ---------------------------------------------------------------------------
;; Predefs expanded
;; ---------------------------------------------------------------------------

(t/deftest predefs-expanded-test
  (let [samples {:integer [1 1.5] :symbol ['sym :sym]
                 :boolean [true 0] :float [(float 1.0) 1]
                 :double [1.0 1] :number [1 "1"]
                 :uuid [#?(:clj (java.util.UUID/randomUUID)
                           :cljs (random-uuid)) "uuid"]
                 :char [\a "a"] :fn [(fn []) 1] :map [{} []]
                 :vector [[] {}] :set [#{} []] :list ['() []]
                 :seq [(seq [1]) 1] :coll [[] 1]
                 :seqable [[] 1] :sequential [[1 2] #{1}]
                 :empty [[] [1]] :some [1 nil] :nil [nil 1]
                 :even [2 1] :odd [1 2] :pos [1 0] :neg [-1 0]
                 :zero [0 1] :pos-int [1 0] :neg-int [-1 1]
                 :nat-int [0 -1]}]
    (doseq [[kw [good bad]] samples]
      (t/testing (str kw)
        (t/is (s/valid? kw good))
        (t/is (not (s/valid? kw bad)))))
    (t/testing ":any accepts everything"
      (t/is (s/valid? :any nil))
      (t/is (s/valid? :any 42)))))

;; ---------------------------------------------------------------------------
;; Predef vs Userdef
;; ---------------------------------------------------------------------------

(t/deftest predef-vs-userdef-test
  ;; user-defined spec for :string should not override built-in
  (s/define :string (constantly false))
  (s/define :life #(= 42 %))
  (t/testing "predef wins"
    (t/is (s/valid? :string "hi"))
    (t/is (not (s/valid? :string 1))))
  (t/testing "user-defined used when no predef"
    (t/is (s/valid? :life 42))
    (t/is (not (s/valid? :life 0)))))

;; ---------------------------------------------------------------------------
;; Mixed examples at the end
;; ---------------------------------------------------------------------------

(s/define :person/name :my.app/string)

(t/deftest mixed-examples
  (t/is (s/valid? :person/name "12312sdfasdf"))
  (t/is (s/valid? [:or :my.app/int :my.app/string] 1))
  (t/is (s/valid? [:and :my.app/int #(< % 18)] 12))
  (t/is (not (s/valid? [:and :my.app/int #(< % 18)] 89)))
  (t/is (s/valid? {:req [:person/name]} {:person/name "alice"}))
  (t/is (not (s/valid? {:req [:person/name]} {:person/name 123}))))
