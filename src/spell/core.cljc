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

(defn- pull [id]
  (get (deref userdefs) id))

(defn- push! [kw thing]
  (swap! userdefs assoc kw thing))

(def def push!)

(defn valid? [spec v]
  (cond (fn? spec) (spec v)
        (get predefs spec) ((get predefs spec) v)
        (pull spec) (valid? (pull spec) v)
        (map? spec) (let [{:keys [req opt]} spec]
                      (and (every? #(valid? % (get v %)) req)
                           (every? #(let [val (get v %)]
                                      (or (nil? val) (valid? % val))) opt)))
        (vector? spec) (let [[op & [a :as col]] spec]
                         (case op
                           :or (some #(valid? % v) col)
                           :and (every? #(valid? % v) col)
                           :vector (and (vector? v) (every? #(valid? a %) v))
                           :list (and (list? v) (every? #(valid? a %) v))
                           :set (and (set? v) (every? #(valid? a %) v))
                           :enum (contains? (set col) v)
                           (u/fail! {:spec spec :value v})))
        :else (u/fail! {:spec spec :value v})))

(defn coerce! [kw v]
  (when-not (valid? kw v)
    (u/fail! {:spec kw :value v}))
  v)
