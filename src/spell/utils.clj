(ns spell.utils
  (:require
   [clojure.pprint :as pp]))

(def all-true?
  (partial every? true?))

(def any-true?
  (partial some true?))

(defn fail! [msg data]
  (println "🛑 spell error:")
  (pp/pprint data)
  (throw (ex-info msg data)))

(defn single-arity? [fn-tail]
  (vector? (first fn-tail)))