(ns spell.instrument
  (:require
   [spell.core :as s]
   [spell.utils :as u]))

(defonce ^:private ^:dynamic *userdefs*
  (atom {}))

(defn push!
  [[ns_ ident arity]
   {:keys [_in _out] :as m}]
  (swap! *userdefs* assoc-in [ns_ ident arity] m))

(defn pull
  [[ns_ ident arity] dir]
  (get-in
   (deref *userdefs*)
   [ns_ ident arity dir]))

(defonce ^:dynamic *level*
  (atom nil))

(defn level! [k]
  (reset! *level* k))

(defn inst! []
  (level! :high))

(defn midst! []
  (level! :low))

(defn unst! []
  (level! nil))

#?(:clj
   (defmacro defnt
     [ident & fn-tail]
     (let [arities (if (u/single-arity? fn-tail)
                     [fn-tail] fn-tail)]
       `(do
          ~@(for [[args specs & _body] arities]
              (let [arity-n (count args)
                    in-specs (-> specs butlast butlast vec)
                    out-spec (last specs)]
                `(push!
                  [(ns-name *ns*) '~ident ~arity-n]
                  {:in ~in-specs :out ~out-spec})))
          (defn ~ident
            ~@(for [[args _specs & body] arities]
                (let [arity-n (count args)]
                  `(~args
                    (if-let [level# (deref *level*)]
                      ;; do validation if any inst level
                      (let [path# [(ns-name *ns*) '~ident ~arity-n]
                            in-specs# (pull path# :in)
                            out-spec# (pull path# :out)
                            err-f# (case level# :high u/fail! :low u/notify identity)]
                        (when level#
                          (doall
                           (map (fn [arg# in-spec#]
                                  (when-not (s/valid? in-spec# arg#)
                                    (err-f# (u/err-data :in (ns-name *ns*)
                                                        '~ident ~arity-n
                                                        arg# in-spec#))))
                                ~args in-specs#)))
                        (let [returned-val# (do ~@body)]
                          (when level#
                            (when-not (s/valid? out-spec# returned-val#)
                              (err-f# (u/err-data :out (ns-name *ns*)
                                                  '~ident ~arity-n
                                                  returned-val# out-spec#))))
                          returned-val#))
                      ;; just run the function body if no inst level
                      (do ~@body))))))))))


(defn inst-coerce! [kw v]
  (let [lv (deref *level*)]
    (if (and lv (not (s/valid? kw v)))
      (let [err-f (case lv :high u/fail!
                        :low u/notify nil)]
        (err-f {:spec kw :value v}))
      v)))

(defmacro tlet
  [bindings & body]
  (let [pairs
        (->> (partition 3 bindings)
             (mapcat (fn [[sym spec value]]
                       [sym `(inst-coerce!
                              ~spec ~value)])))]
    `(let [~@pairs]
       ~@body)))

(comment
  (tlet [a :int 1.2]
    (inc a))
;; when *level* is :low, error is thrown, why?
  )

(comment
  (inst!)
  (midst!)
  (unst!))

(comment
  (deref *userdefs*)
  :=> {'spell.core-test
       {'square {1 {:out :int, :in [:int]}},
        'sum {1 {:out :int, :in [:int]}, 2 {:out :int, :in [:int :int]}}},
       'user {'passer {1 {:out :int, :in [:int]}}}})
