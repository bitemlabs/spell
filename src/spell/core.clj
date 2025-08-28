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
  (let [abbr-f (store.abbr/pull spec)
        pred (get (store.preds/pull) spec)]
    (cond abbr-f (valid? abbr-f v)
          pred (valid? pred v)
          (fn? spec) (spec v)
          (map? spec) (u/all-true?
                       (concat (map #(valid? % (get v %))
                                    (:req spec))
                               (map #(or (nil? (get v %))
                                         (valid? % (get v %)))
                                    (:opt spec))))
          (vector? spec) (let [[op & col] spec]
                           (case op
                             :or (u/any-true? (map #(valid? % v) col))
                             :and (u/all-true? (map #(valid? % v) col))
                             :vector (and (vector? v) (u/all-true? (map #(valid? (first col) %) v)))
                             :list (and (list? v) (u/all-true? (map #(valid? (first col) %) v)))
                             :set (and (set? v) (u/all-true? (map #(valid? (first col) %) v)))
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
       ~@(for [[args sigs & _body] arities]
           (let [arity-n (count args)
                 in-sigs (-> sigs butlast butlast vec)
                 out-sig (last sigs)]
             `(store.inst/push!
               [(ns-name *ns*) '~ident ~arity-n]
               {:in ~in-sigs :out ~out-sig})))
       (defn ~ident
         ~@(for [[args _sigs & body] arities]
             (let [arity-n (count args)]
               `(~args
                 (let [path# [(ns-name *ns*) '~ident ~arity-n]
                       in# (store.inst/pull path# :in)
                       out# (store.inst/pull path# :out)
                       level# (store.config/pull :level)
                       f# (case level# :high u/fail! :low u/notify nil identity)]
                   (when level#
                     (doall
                      (map (fn [arg# sig#]
                             (when-not (valid? sig# arg#)
                               (f# (u/err-data :in (ns-name *ns*)
                                               '~ident ~arity-n arg# sig#))))
                           ~args in#)))
                   (let [ret# (do ~@body)]
                     (when level#
                       (when-not (valid? out# ret#)
                         (f# {} (u/err-data :out (ns-name *ns*)
                                            '~ident ~arity-n ret# out#))))
                     ret#)))))))))

(comment
  (inst!)
  (midst!)
  (unst!))
