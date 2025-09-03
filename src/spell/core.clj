(ns spell.core
  (:require
   [spell.store.abbreviations :as store.abbr]
   [spell.store.config :as store.config]
   [spell.store.instrument :as store.inst]
   [spell.store.predicates :as store.preds]
   [spell.utils :as u]))

(def df store.preds/push!)

(defn inst! []
  (store.config/level! :high))

(defn midst! []
  (store.config/level! :low))

(defn unst! []
  (store.config/level! nil))

(defn valid? [spec v]
  (let [predef-fn (store.abbr/pull spec)
        userdef-fn (get (store.preds/pull) spec)]
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

(defmacro defnt
  [ident & fn-tail]
  (let [arities (if (u/single-arity? fn-tail)
                  [fn-tail] fn-tail)]
    `(do
       ~@(for [[args specs & _body] arities]
           (let [arity-n (count args)
                 in-specs (-> specs butlast butlast vec)
                 out-spec (last specs)]
             `(store.inst/push!
               [(ns-name *ns*) '~ident ~arity-n]
               {:in ~in-specs :out ~out-spec})))
       (defn ~ident
         ~@(for [[args _specs & body] arities]
             (let [arity-n (count args)]
               `(~args
                 (if-let [level# (store.config/pull :level)]
                   ;; do validation if any inst level
                   (let [path# [(ns-name *ns*) '~ident ~arity-n]
                         in-specs# (store.inst/pull path# :in)
                         out-spec# (store.inst/pull path# :out)
                         err-f# (case level# :high u/fail! :low u/notify nil)]
                     (when level#
                       (doall
                        (map (fn [arg# in-spec#]
                               (when-not (valid? in-spec# arg#)
                                 (err-f# (u/err-data :in (ns-name *ns*)
                                                     '~ident ~arity-n
                                                     arg# in-spec#))))
                             ~args in-specs#)))
                     (let [returned-val# (do ~@body)]
                       (when level#
                         (when-not (valid? out-spec# returned-val#)
                           (err-f# (u/err-data :out (ns-name *ns*)
                                               '~ident ~arity-n
                                               returned-val# out-spec#))))
                       returned-val#))
                   ;; just run the function body if no inst level
                   (do ~@body)))))))))

(comment
  (inst!)
  (midst!)
  (unst!))
