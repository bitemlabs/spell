(ns spell.core
  (:require
   [spell.utils :as u])
  (:import [clojure.lang IFn IPersistentVector]))

(def predefs
  {:any any? :int int? :integer integer? :string string?
   :keyword keyword? :symbol symbol? :boolean boolean?
   :float float? :double double? :number number?
   :uuid uuid? :char char? :fn fn? :map map?
   :vector vector? :set set? :list list? :seq seq?
   :coll coll? :seqable seqable? :sequential sequential?
   :empty empty? :some some? :nil nil? :even even?
   :odd odd? :pos pos? :neg neg? :zero zero?
   :pos-int pos-int? :neg-int neg-int? :nat-int nat-int?})

(declare compile!)

(defonce ^:private userdefs
  (atom {}))

(defonce ^:private compiled
  (atom {}))

(defn- cache! [k v]
  (swap! compiled assoc k v)
  v)

(defn define [k thing]
  (swap! userdefs assoc k thing)
  (compile! k)
  true)

(defn ^:private logical-and [preds]
  (let [preds ^objects (into-array IFn preds)
        cnt (alength preds)]
    (fn [v]
      (loop [i 0]
        (if (= i cnt)
          true
          (if (.invoke ^IFn (aget preds i) v)
            (recur (unchecked-inc-int i))
            false))))))

(defn- logical-or [preds]
  (let [preds ^objects (into-array IFn preds)
        cnt (alength preds)]
    (fn [v]
      (loop [i 0]
        (cond
          (= i cnt) false
          (.invoke ^IFn (aget preds i) v) true
          :else (recur (unchecked-inc-int i)))))))

(defn- logical-pred [k xs]
  (let [preds (mapv compile! xs)]
    (case k
      :or (logical-or preds)
      :and (logical-and preds))))

(defn- collection-pred [col-type-f xs]
  (let [elem-pred ^IFn (compile! (first xs))]
    (fn [v]
      (and (col-type-f v)
           (if (vector? v)
             (let [^IPersistentVector vv v
                   cnt (count vv)]
               (loop [i 0]
                 (if (= i cnt) true
                   (if (.invoke elem-pred (nth vv i))
                     (recur (unchecked-inc-int i))
                     false))))
             (loop [s (seq v)]
               (if (nil? s) true
                 (if (.invoke elem-pred (first s))
                   (recur (next s))
                   false))))))))

(defn- map-pred [{:keys [req opt]}]
  (let [req (or req []) opt (or opt [])
        req-ks (object-array req)
        req-ps (object-array (map compile! req))
        req-cnt (alength req-ks)
        opt-ks (object-array opt)
        opt-ps (object-array (map compile! opt))
        opt-cnt (alength opt-ks)]
    (fn [m]
      (and (map? m)
           ;; required keys: must be present AND pass predicate
           (loop [i 0]
             (if (= i req-cnt) true
                 (let [k (aget req-ks i)
                       ^clojure.lang.IFn p (aget req-ps i)]
                   (if (and (contains? m k)
                            (.invoke p (get m k)))
                     (recur (unchecked-inc-int i))
                     false))))
           ;; optional keys: if present and non-nil, must pass predicate.
           ;; if absent, OK. if present but nil, OK (per your current rule).
           (loop [i 0]
             (if (= i opt-cnt) true
                 (let [k (aget opt-ks i)
                       ^clojure.lang.IFn p (aget opt-ps i)]
                   (if-let [v (get m k)]
                     (if (or (nil? v) (.invoke p v))
                       (recur (unchecked-inc-int i))
                       false)
                     (recur (unchecked-inc-int i))))))))))

(defn compile! [x]
  (or (get @compiled x)
      (let [pre-fn (get predefs x)
            userdef (get @userdefs x)]
        (cache!
         x (cond (fn? x) x
                 pre-fn pre-fn
                 userdef (if (fn? userdef) userdef
                             (compile! userdef))
                 (vector? x) (let [[op & xs] x]
                               (case op
                                 :or (logical-pred :or xs)
                                  :and (logical-pred :and xs)
                                 :vector (collection-pred vector? xs)
                                 :list (collection-pred list? xs)
                                 :set (collection-pred set? xs)
                                 :enum (let [s (set xs)]
                                         (fn [v] (contains? s v)))
                                 (fn [_] false)))
                 (map? x) (map-pred x)
                 :else #(u/fail! {:spec x}))))))

(defn valid? [spec v]
  (let [pred (compile! spec)]
    (pred v)))

(defn coerce! [kw v]
  (when-not (valid? kw v)
    (u/fail! {:spec kw :value v}))
  v)
