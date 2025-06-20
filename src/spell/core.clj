(ns spell.core)

(defonce ^:private config
  (atom {:inst-level :none}))

(defn inst-level! [k]
  (swap! config assoc
         :inst-level k))

(defn inst! []
  (inst-level! :high))

(defn midst! []
  (inst-level! :low))

(defn unst! []
  (inst-level! :none))

(defonce ^:private defs
  (atom {}))

(defn get-defs []
  (deref defs))

(def abbreviations
  {:int int?
   :string string?
   :keyword keyword?})

(defn df [kw thing]
  (swap! defs assoc kw thing))

(def all-true?
  (partial every? true?))

(def any-true?
  (partial some true?))

(defn fail! [msg]
  (throw (ex-info msg {:spell :error})))

(defn valid? [spec v]
  (let [abbr-f (get abbreviations spec)
        pred (get (get-defs) spec)]
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

(defmacro defnt
  [ident args sigs & body]
  (let [in-sigs (butlast sigs)
        out-sig (last sigs)]
    `(defn ~ident ~args
       (let [level# (:inst-level @config)
             f# (case level#
                  :high fail!
                  :low println
                  identity)]
         (when-not (= :none level#)
           (doall
            (map (fn [arg# sig#]
                   (when-not (valid? sig# arg#)
                     (f# (str "Invalid input to "
                              '~ident ": " arg#))))
                 (list ~@args) (list ~@in-sigs))))
         (let [ret# (do ~@body)]
           (when-not (= :none level#)
             (when-not (valid? ~out-sig ret#)
               (f# (str "Invalid output from "
                        '~ident ": " ret#))))
           ret#)))))