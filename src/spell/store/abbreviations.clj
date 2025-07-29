(ns spell.store.abbreviations)

(def ^:private db
  (atom {:int int?
         :string string?
         :keyword keyword?}))

(defn pull [k]
  (-> db deref (get k)))