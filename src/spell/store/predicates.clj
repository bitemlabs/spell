(ns spell.store.predicates)

(defonce ^:private db
  (atom {}))

(defn pull []
  (deref db))

(defn push! [kw thing]
  (swap! db assoc kw thing))