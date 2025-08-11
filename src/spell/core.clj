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
                             (u/fail! "invalid logical operator in vector spec"
                                    {:spec spec :value v})))
          :else (u/fail! "invalid spec form"
                       {:spec spec :value v}))))

(defn coerce [kw v]
  (when-not (valid? kw v)
    (u/fail! "coercion failed"
           {:spec kw :value v}))
  v)

(defmacro defnt
  [ident & fn-tail]
  (let [arities (if (u/single-arity? fn-tail)
                  [fn-tail] fn-tail)]
    `(defn ~ident
       ~@(for [[args sigs & body] arities]
           (let [in-sigs (-> sigs butlast butlast vec)
                 out-sig (last sigs)
                 f-symb (gensym "f")]
             `(~args
               (let [~f-symb (case (store.config/pull :level)
                               :high u/fail!
                               :low println
                               nil identity)]
                 ;; input validation
                 ~@(for [[arg-symb in-sig] (map vector args in-sigs)]
                     `(when-not (valid? ~in-sig ~arg-symb)
                        (~f-symb "inst input fail"
                                 {:ident '~ident
                                  :arg ~arg-symb
                                  :spec ~in-sig})))
                 ;; output validation
                 (let [ret# (do ~@body)]
                   (when-not (valid? ~out-sig ret#)
                     (~f-symb "inst output fail"
                              {:ident '~ident
                               :return ret#
                               :spec ~out-sig}))
                   ret#))))))))

(comment
  (inst!)
  (midst!)
  (unst!))
