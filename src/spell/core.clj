(ns spell.core
  (:require
   [spell.store.instrument :as store.inst]))

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

(defn get-config []
  (deref config))

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

(defn fail! [msg data]
  (println "spell error:")
  (println data)
  (throw (ex-info msg data)))

(defn valid? [spec v]
  (let [abbr-f (get abbreviations spec)
        pred (get (get-defs) spec)]
    (cond abbr-f (valid? abbr-f v)
          pred (valid? pred v)
          (fn? spec) (spec v)
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
                             (fail! "invalid logical operator in vector spec"
                                    {:spec spec :value v})))
          :else (fail! "invalid spec form"
                       {:spec spec :value v}))))

(defn coerce [kw v]
  (when-not (valid? kw v)
    (fail! "coercion failed"
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
                       f# fail!]
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