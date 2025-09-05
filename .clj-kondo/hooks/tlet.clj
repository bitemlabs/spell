(ns hooks.tlet
  (:require [clj-kondo.hooks-api :as api]))

(defn tlet [{:keys [node]}]
  (let [[_ bindings & body] (:children node)
        binding-children (:children bindings)
        pairs (->> binding-children
                   (partition 3)
                   (mapcat (fn [[sym _spec val]] [sym val])))
        new-bindings (api/vector-node pairs)
        new-node (api/list-node (list* (api/token-node 'let)
                                       new-bindings
                                       body))]
    {:node new-node}))
