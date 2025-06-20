(ns spell.core-test
  (:require
   [clojure.test :as t]
   [spell.core :as s]))

(t/deftest fail!-test
  (t/testing "fail! throws ex-info"
    (t/is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"fail" (s/fail! "fail")))))

(t/deftest all-true?-test
  (t/are [in exp]
         (= exp (s/all-true? in))
    [true true true] true
    [1 true true] false
    [true nil true] false
    [true true false] false))

(def sample-db-unqualified
  {:age int?
   :first-name string?
   :state :string
   :person {:req #{:first-name
                   :address}
            :opt #{:age}}
   :address {:req #{:state}}})

(def sample-db-qualified
  {:person/age [:and int? pos?]
   :person/first-name string?
   :address/state [:or [:and string? #(< (count %) 3)] :keyword]
   :person/address :address/object
   :person/object {:req #{:person/first-name
                          :person/address}
                   :opt #{:person/age}}
   :address/object {:req #{:address/state}}})

(t/deftest valid?-test
  (t/testing "unqualified schema"
    (with-redefs [s/get-defs
                  (constantly
                   sample-db-unqualified)]
      (t/are [x v] (s/valid? x v)
        :age 56
        :first-name "Mickey"
        :state "CA"
        :person {:first-name "Miro"
                 :age 67
                 :address {:state "CA"}})))
  (t/testing "qualified schema"
    (with-redefs [s/get-defs
                  (constantly
                   sample-db-qualified)]
      (t/are [kw v] (s/valid? kw v)
        :person/age 56
        :person/first-name "Mickey"
        :address/state "CA"
        :address/state :california
        :person/object {:person/first-name "Miro"
                        :person/age 67
                        :person/address
                        {:address/state "CA"}}))))