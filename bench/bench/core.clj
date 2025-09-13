(ns bench.core
  (:require
   [criterium.core :as c]
   [clojure.spec.alpha :as clj-spec]
   [malli.core :as malli]
   [spell.core :as spell]))

;; ----------------------------
;; Sample data
;; ----------------------------

(def valid-user
  {:id 123
   :name "Ada"
   :age 35})

(def invalid-user
  {:id "oops"
   :name 777})

;; ----------------------------
;; clojure.spec schema + valid?
;; ----------------------------

(clj-spec/def ::id int?)
(clj-spec/def ::name string?)
(clj-spec/def ::age int?)
(clj-spec/def ::user
  (clj-spec/keys :req-un
                 [::id ::name ::age]))

(defn spec-valid? [x]
  (clj-spec/valid? ::user x))

;; ----------------------------
;; Malli schema + valid?
;; ----------------------------

(def m-user
  [:map
   [:id int?]
   [:name string?]
   [:age int?]])

(def m-valid? (malli/validator m-user)) ; compiled validator

(defn malli-valid? [x]
  (m-valid? x))

;; ----------------------------
;; Spell schema + valid?
;; (Assumes Spell accepts an inline schema vector form)
;; ----------------------------

(spell/def :id :int)
(spell/def :name :string)
(spell/def :age :int)
(spell/def :user {:req [:id :name :age]})

(defn spell-valid? [x]
  (spell/valid? :user x))

;; ----------------------------
;; Bench helpers
;; ----------------------------

(defmacro bench! [label expr]
  `(do
     (println "\n=== " ~label " ===")
     (c/quick-bench ~expr)))

(defn -main [& _]
  (println "Microbench: `valid?` only (Spec vs Malli vs Spell). Valid + invalid.")

  ;; VALID input
  (bench! "Spec  valid? (valid)"
    (spec-valid? valid-user))

  (bench! "Malli valid? (valid, compiled)"
    (malli-valid? valid-user))

  (bench! "Spell valid? (valid)"
    (spell-valid? valid-user))

  ;; INVALID input
  (bench! "Spec  valid? (invalid)"
    (spec-valid? invalid-user))

  (bench! "Malli valid? (invalid, compiled)"
    (malli-valid? invalid-user))

  (bench! "Spell valid? (invalid)"
    (spell-valid? invalid-user))

  (println "\nDone."))

(comment
  (-main {}))
