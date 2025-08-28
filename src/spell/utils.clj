(ns spell.utils
  (:require
   [clojure.pprint :as pp]))

(def all-true?
  (partial every? true?))

(def any-true?
  (partial some true?))

(defn err-data [direction ns ident arity-n arg sig]
  {:direction direction
   :ns ns :ident ident :arity-version arity-n
   :argument arg :validation-signature sig})

(defn fail! [m]
  (throw
   (ex-info
    "🛑 error thrown by spell" m)))

(defn notify [m]
  (println "🛑 error notified by spell")
  (pp/pprint m))

(defn single-arity? [fn-tail]
  (vector? (first fn-tail)))
