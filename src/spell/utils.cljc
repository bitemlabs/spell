(ns spell.utils
  #?(:clj  (:require [clojure.pprint :as pp])
     :cljs (:require [cljs.pprint    :as pp])))

(defn nilable-pred [f]
  (fn [spec x]
    (or (nil? x) (f spec x))))

(defn err-data [direction qident arity-n arg sig]
  {:direction direction
   :ident qident :arity-version arity-n
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
