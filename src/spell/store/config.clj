(ns spell.store.config)

(defonce ^:private db
  (atom {}))

(defn level! [k]
  (swap! db assoc :level k))

(defn pull [k]
  (-> db deref (get k)))