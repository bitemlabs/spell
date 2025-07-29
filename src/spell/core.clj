(ns spell.core
  (:require
   [spell.store.abbreviations :as store.abbr]
   [spell.store.instrument :as store.inst]
   [spell.store.predicates :as store.preds]
   [spell.utils :as u]))

(def df store.preds/push!)

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

(defn get-config []
  (deref config))

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

(defn single-arity? [fn-tail]
  (vector? (first fn-tail)))

(defmacro defnt
  [ident & fn-tail]
  (let [arities (if (single-arity? fn-tail)
                  [fn-tail] fn-tail)]
    `(do
       ~@(for [[args sigs & _body] arities]
           (let [arity (count args)
                 in-sigs (-> sigs butlast butlast vec)
                 out-sig (last sigs)]
             `(store.inst/push!
               [(ns-name *ns*) '~ident ~arity]
               {:in ~in-sigs :out ~out-sig})))
       (defn ~ident
         ~@(for [[args _sigs & body] arities]
             (let [arity (count args)]
               `(~args
                 (let [level# (:inst-level (get-config))
                       path# [(ns-name *ns*) '~ident ~arity]
                       in# (store.inst/pull path# :in)
                       out# (store.inst/pull path# :out)
                       f# u/fail!]
                   (doall
                    (map (fn [arg# sig#]
                           (when-not (valid? sig# arg#)
                             (f# "inst input fail"
                                 {:ns (ns-name *ns*)
                                  :ident '~ident
                                  :arity ~arity
                                  :arg arg#
                                  :reason "...."})))
                         ~args in#))
                   (let [ret# (do ~@body)]
                     (when-not (valid? out# ret#)
                       (f# "inst output fail"
                           {:ns (ns-name *ns*)
                            :ident '~ident
                            :arity ~arity
                            :reason "...."}))
                     ret#)))))))))