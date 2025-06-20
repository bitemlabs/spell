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

(defn fail! [msg]
  (throw (ex-info msg {:spell :error})))

(defn valid? [spec v]
  (let [abbr-f (get abbreviations spec)
        pred (get (get-preds) spec)]
    (cond abbr-f (valid? abbr-f v)
          pred (valid? pred v)
          (fn? spec) (try (spec v) (catch Exception _ false))
          (map? spec) (all-true?
                       (concat (map #(valid? % (get v %))
                                    (:req spec))
                               (map #(or (nil? (get v %))
                                         (valid? % (get v %)))
                                    (:opt spec))))
          (vector? spec) (let [[op & col] spec]
                           (case op
                             :or (any-true? (map #(valid? % v) col))
                             :and (all-true? (map #(valid? % v) col))
                             (fail! "operator is not correct")))
          :else (fail! "indicator at the first arg doesn't match"))))

(defn coerce [kw v]
  (when-not (valid? kw v)
    (throw (ex-info "oiu" {})))
  v)

(comment
  (df :person/username [:or :keyword [:and :string #(< 4 (count %))]])
  (df :person/username [:and :string #(< 4 (count %))])
  @preds
  (valid? :person/username :darren))