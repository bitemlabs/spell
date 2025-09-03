(ns spell.store.instrument)

(defonce ^:private db
  (atom {}))

(defn push!
  [[ns_ ident arity]
   {:keys [_in _out] :as m}]
  (swap! db assoc-in [ns_ ident arity] m))

(defn pull
  [[ns_ ident arity] dir]
  (get-in
   (deref db)
   [ns_ ident arity dir]))

(comment
  (deref db)
  :=> {'spell.core-test
       {'square {1 {:out :int, :in [:int]}},
        'sum {1 {:out :int, :in [:int]}, 2 {:out :int, :in [:int :int]}}},
       'user {'passer {1 {:out :int, :in [:int]}}}})
