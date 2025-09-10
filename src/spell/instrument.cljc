(ns spell.instrument
  (:require
   [spell.core :as s]
   [spell.utils :as u]))

(defonce ^:private ^:dynamic *userdefs*
  (atom {}))

(defn push!
  [[qvar arity]
   {:keys [_in _out] :as m}]
  (swap! *userdefs* assoc-in [qvar arity] m))

(defn pull
  [[qvar arity] dir]
  (get-in
   (deref *userdefs*)
   [qvar arity dir]))

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
   (defmacro tdefn
     [ident & fn-tail]
     (let [arities (if (u/single-arity? fn-tail) [fn-tail] fn-tail)
           reg-forms
           (for [[args specs & _body] arities]
             (let [arity-n  (count args)
                   in-specs (-> specs butlast butlast vec)
                   out-spec (last specs)]
               `(push! [#'~ident ~arity-n]
                       {:in ~in-specs :out ~out-spec})))]
       `(do
          ;; 1) Define the function first so #'ident exists
          (defn ~ident
            ~@(for [[args _specs & body] arities]
                (let [arity-n (count args)]
                  `(~args
                    (if-let [level# (deref *level*)]
                      ;; do validation if any inst level
                      (let [path#      [#'~ident ~arity-n]
                            in-specs#  (pull path# :in)
                            out-spec#  (pull path# :out)
                            err-f#     (case level# :high u/fail!
                                              :low  u/notify
                                              identity)]
                        ;; in-checks
                        (when level#
                          (doall
                           (map (fn [arg# in-spec#]
                                  (when-not (s/valid? in-spec# arg#)
                                    (err-f# (u/err-data :in #'~ident ~arity-n
                                                        arg# in-spec#))))
                                [~@args] in-specs#)))
                        ;; body + out-check
                        (let [returned-val# (do ~@body)]
                          (when level#
                            (when-not (s/valid? out-spec# returned-val#)
                              (err-f# (u/err-data :out #'~ident ~arity-n
                                                  returned-val# out-spec#))))
                          returned-val#))
                      ;; just run the function body if no inst level
                      (do ~@body))))))

          ;; 2) Now that #'ident exists, register specs keyed by the var
          ~@reg-forms
          #'~ident))))

(defn inst-coerce! [kw v]
  (let [lv (deref *level*)]
    (if (and lv (not (s/valid? kw v)))
      (let [err-f (case lv :high u/fail!
                        :low  u/notify
                        nil)]
        (when err-f (err-f {:spec kw :value v}))
        v)
      v)))

(defmacro tlet
  [bindings & body]
  (let [pairs
        (->> (partition 3 bindings)
             (mapcat (fn [[sym spec value]]
                       [sym `(inst-coerce! ~spec ~value)])))]
    `(let [~@pairs]
       ~@body)))

(comment
  (inst!)
  (midst!)
  (unst!)

  ;; peek at registry (keys are vars and arities)
  (deref *userdefs*)
  ;; => {#'some.ns/square {1 {:out :int, :in [:int]}},
  ;;     #'some.ns/sum    {1 {:out :int, :in [:int]}
  ;;                       2 {:out :int, :in [:int :int]}}}
  )
