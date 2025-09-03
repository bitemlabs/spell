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
  (t/is (not (s/valid? :string 42))))

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
