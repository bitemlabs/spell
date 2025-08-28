(ns spell.utils
  (:require
   [clojure.pprint :as pp]))

(defn nilable-pred [f]
  (fn [spec x]
    (or (nil? x) (f spec x))))

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
