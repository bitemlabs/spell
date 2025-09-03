(ns spell.core
  (:require
   [spell.utils :as u]))

(def predefs
  {:int int?
   :string string?
   :keyword keyword?})

(defonce ^:private userdefs
  (atom {}))

(defn- pull [id]
  (get (deref userdefs) id))

(defn- push! [kw thing]
  (swap! userdefs assoc kw thing))

(def def push!)

(defn valid? [spec v]
  (let [predef-fn (pull spec)
        userdef-fn (get predefs spec)]
    (cond userdef-fn (valid? userdef-fn v)
          predef-fn (valid? predef-fn v)
          (fn? spec) (spec v)
          (map? spec) (let [{:keys [req opt]} spec
                            nilable-valid? (u/nilable-pred valid?)]
                        (and (every? #(valid? % (get v %)) req)
                             (every? #(nilable-valid? % (get v %)) opt)))
          (vector? spec) (let [[op & [a :as col]] spec
                               pred-v #(valid? % v)
                               pred-a #(valid? a %)]
                           (case op
                             :or (some pred-v col)
                             :and (every? pred-v col)
                             :vector (and (vector? v) (every? pred-a v))
                             :list (and (list? v) (every? pred-a v))
                             :set (and (set? v) (every? pred-a v))
                             (u/fail! {:spec spec :value v})))
          :else (u/fail! {:spec spec :value v}))))

(defn coerce [kw v]
  (when-not (valid? kw v)
    (u/fail! {:spec kw :value v}))
  v)
