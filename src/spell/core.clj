(ns spell.core)

(defonce ^:private configs
  (atom {:inst-level :none}))

(defn inst-level! [k]
  (swap! configs assoc :inst-level k))

(defn inst! []
  (inst-level! :high))

(defn midst! []
  (inst-level! :low))

(defn unst! []
  (inst-level! :none))

(defonce ^:private preds
  (atom {}))

(defn get-preds []
  (deref preds))

(def abbreviations
  {:int int?
   :string string?
   :keyword keyword?})

(defn df [kw thing]
  (swap! preds assoc kw thing))

(def all-true?
  (partial every? true?))

(def any-true?
  (partial some true?))

(defn valid? [kw-or-fn v]
  (cond (fn? kw-or-fn) (kw-or-fn v)
        (keyword? kw-or-fn)
        (if-let [f (get abbreviations kw-or-fn)]
          (f v)
          (let [pulled (get (get-preds) kw-or-fn)]
            (cond (map? pulled)
                  (all-true?
                   (concat (map #(valid? % (get v %)) (:req pulled))
                           (map #(or (nil? (get v %)) (valid? % (get v %))) (:opt pulled))))
                  (vector? pulled) (let [[op & col] pulled]
                                     (case op
                                       :or (any-true? (map #(valid? % v) col))
                                       :and (all-true? (map #(valid? % v) col))))
                  (keyword? pulled)
                  (valid? pulled v)
                  (fn? pulled)
                  (pulled v))))
        :else (throw (ex-info "first arg must be either keyword or function"
                              {:error :type}))))

(defn coerce [kw v]
  (when-not (valid? kw v)
    (throw (ex-info "oiu" {})))
  v)

()