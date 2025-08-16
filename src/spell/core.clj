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
    `(do
       ~@(for [[args sigs & _body] arities]
           (let [[req tail] (split-with #(not= '& %) args)
                 tail (vec tail)
                 opt (if (seq tail)
                       (let [o (second tail)]
                         (if (vector? o) o [o]))
                       [])
                 arity (+ (count req) (count opt))
                 in-sigs (-> sigs butlast butlast vec)
                 out-sig (last sigs)]
             `(store.inst/push!
               [(ns-name *ns*) '~ident ~arity]
               {:in ~in-sigs :out ~out-sig})))
       (defn ~ident
         ~@(for [[args _sigs & body] arities]
             (let [[req tail] (split-with #(not= '& %) args)
                   tail (vec tail)
                   opt (if (seq tail)
                         (let [o (second tail)]
                           (if (vector? o) o [o]))
                         [])
                   arity (+ (count req) (count opt))
                   all-args (vec (concat req opt))
                   optionals (vec (concat (repeat (count req) false)
                                         (repeat (count opt) true)))]
               `(~args
                 (let [path# [(ns-name *ns*) '~ident ~arity]
                       in# (store.inst/pull path# :in)
                       out# (store.inst/pull path# :out)
                       f# (case (store.config/pull :level)
                            :high u/fail!
                            :low println
                            nil identity)]
                   (doall
                    (map (fn [arg# sig# opt?#]
                           (when (or (not opt?#) (some? arg#))
                             (when-not (valid? sig# arg#)
                               (f# "inst input fail"
                                   {:ns (ns-name *ns*)
                                    :ident '~ident
                                    :arity ~arity
                                    :arg arg#
                                    :reason "...."}))))
                         [~@all-args] in# ~optionals))
                   (let [ret# (do ~@body)]
                     (when-not (valid? out# ret#)
                       (f# "inst output fail"
                           {:ns (ns-name *ns*)
                            :ident '~ident
                            :arity ~arity
                            :reason "...."}))
                     ret#)))))))))

(comment
  (inst!)
  (midst!)
  (unst!))
