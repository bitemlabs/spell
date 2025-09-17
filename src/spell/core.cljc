(ns spell.core
  (:require
   [spell.utils :as u]))

(def predefs
  {:any any? :int int? :integer integer? :string string?
   :keyword keyword? :symbol symbol? :boolean boolean?
   :float float? :double double? :number number?
   :uuid uuid? :char char? :fn fn? :map map?
   :vector vector? :set set? :list list? :seq seq?
   :coll coll? :seqable seqable? :sequential sequential?
   :empty empty? :some some? :nil nil? :even even?
   :odd odd? :pos pos? :neg neg? :zero zero?
   :pos-int pos-int? :neg-int neg-int? :nat-int nat-int?})

(defonce ^:private userdefs
  (atom {}))

(defonce ^:private compiled
  (atom {}))

(defn def [k thing]
  (swap! userdefs assoc k thing)
  (swap! compiled dissoc k))

(defn- cache! [k v]
  (swap! compiled assoc k v)
  v)

(declare compile!)

(defn logical-pred [agg-f xs]
  (let [preds (mapv compile! xs)]
    (fn [v] (agg-f #(% v) preds))))

(defn collection-pred [col-type-f xs]
  (let [elem-pred (compile! (first xs))]
    (fn [v] (and (col-type-f v)
                 (every? elem-pred v)))))

(defn map-pred [{:keys [req]}]
  (let [req-preds (mapv (fn [k] [k (compile! k)]) req)]
    (fn [m]
      (and (map? m)
           (every? (fn [[k p]] (p (get m k))) req-preds)))))

(defn compile! [x]
  (or (get @compiled x)
      (let [pre-fn (get predefs x)
            userdef (get @userdefs x)]
        (cache!
         x (cond (fn? x) x
                 pre-fn pre-fn
                 userdef (if (fn? userdef) userdef
                             (compile! userdef))
                 (vector? x) (let [[op & xs] x]
                               (case op
                                 :or (logical-pred some xs)
                                 :and (logical-pred every? xs)
                                 :vector (collection-pred vector? xs)
                                 :list (collection-pred list? xs)
                                 :set (collection-pred set? xs)
                                 :enum #(contains? (set xs) %)
                                 (fn [_] false)))
                 (map? x) (map-pred x)
                 :else #(u/fail! {:spec x}))))))

(defn valid? [spec v]
  (let [pred (compile! spec)]
    (pred v)))

(defn coerce! [kw v]
  (when-not (valid? kw v)
    (u/fail! {:spec kw :value v}))
  v)
