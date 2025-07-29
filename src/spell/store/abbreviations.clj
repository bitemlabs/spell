(ns spell.store.abbreviations)

(defonce ^:private db
  (atom {:int int?
         :string string?
         :keyword keyword?}))

(defn pull [k]
  (-> db deref (get k)))