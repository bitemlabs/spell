(ns bench.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [malli.core :as m]
    [spell.core :as spell]))

;; -----------------------------
;; silent microbench helpers
;; -----------------------------

(defn- now-ns ^long []
  (System/nanoTime))

(defn- warmup! [f n]
  (loop [i (long 0)]
    (when (< i n)
      (f)
      (recur (unchecked-inc i)))))

(defn bench-avg-ns
  "Runs f exactly `iters` times. Returns {:avg_ns .. :total_ns .. :iters ..}."
  [f ^long iters]
  (warmup! f 20000)
  (let [t0 (now-ns)]
    (loop [i (long 0)]
      (when (< i iters)
        (f)
        (recur (unchecked-inc i))))
    (let [total (- (now-ns) t0)]
      {:avg_ns   (double (/ total iters))
       :total_ns total
       :iters    iters})))

;; -----------------------------
;; sample values
;; -----------------------------

(def samples
  {:int-valid 42 :int-invalid "x"
   :string-valid "Ada" :string-invalid 777
   :keyword-valid :ok :keyword-invalid "ok"
   :and-valid 5 :and-invalid -3 :or-valid "yo"
   :or-invalid :nope :vector-valid [1 2 3 4 5]
   :vector-invalid [1 "x" 3] :list-valid '(1 2 3)
   :list-invalid '(1 :x 3) :set-valid #{:a :b :c}
   :set-invalid #{:a "b" :c}
   :map-valid {:id 1 :name "Ada" :age 35}
   :map-invalid {:id "oops" :name 777}})

;; -----------------------------
;; Spec validators
;; -----------------------------

(def spec-validators
  {:raw-int (fn [v] (s/valid? int? v))
   :raw-string (fn [v] (s/valid? string? v))
   :raw-keyword (fn [v] (s/valid? keyword? v))
   :and (fn [v] (s/valid? (s/and int? pos-int?) v))
   :or (fn [v] (s/valid? (s/or :s string? :i int?) v))
   :vector (fn [v] (s/valid? (s/coll-of int? :kind vector?) v))
   :list (fn [v] (s/valid? (s/coll-of int? :kind list?) v))
   :set (fn [v] (s/valid? (s/coll-of keyword? :kind set? :distinct true) v))
   :map (do (s/def ::id int?)
            (s/def ::name string?)
            (s/def ::age int?)
            (let [spec (s/keys :req-un [::id ::name ::age])]
              (fn [v] (s/valid? spec v))))})

;; -----------------------------
;; Malli validators (compiled)
;; -----------------------------

(def malli-validators
  (let [v (fn [schema] (m/validator schema))]
    {:raw-int (v int?)
     :raw-string (v string?)
     :raw-keyword (v keyword?)
     :and (v [:and int? pos-int?])
     :or (v [:or string? int?])
     :vector (v [:vector int?])
     :list (v [:sequential int?])
     :set (v [:set keyword?])
     :map (v [:map
              [:id int?]
              [:name string?]
              [:age int?]])}))

;; -----------------------------
;; Spell validators (compiled)
;; -----------------------------

(spell/define :id :int)
(spell/define :name :string)
(spell/define :age :int)
(spell/define :user {:req [:id :name :age]})

(def spell-validators
  {:raw-int (fn [v] (spell/valid? :int v))
   :raw-string  (fn [v] (spell/valid? :string v))
   :raw-keyword (fn [v] (spell/valid? :keyword v))
   :and (let [schema [:and :int :pos-int]]
          (fn [v] (spell/valid? schema v)))
   :or (let [schema [:or :string :int]]
         (fn [v] (spell/valid? schema v)))
   :vector (let [schema [:vector :int]]
             (fn [v] (spell/valid? schema v)))
   :list (let [schema [:list :int]]
           (fn [v] (spell/valid? schema v)))
   :set (let [schema [:set :keyword]]
          (fn [v] (spell/valid? schema v)))
   :map (fn [v] (spell/valid? :user v))})

;; -----------------------------
;; matrix + runner
;; -----------------------------

(def cases
  [[:raw-int :int-valid :int-invalid]
   [:raw-string :string-valid :string-invalid]
   [:raw-keyword :keyword-valid :keyword-invalid]
   [:and :and-valid :and-invalid]
   [:or :or-valid :or-invalid]
   [:vector :vector-valid :vector-invalid]
   [:list :list-valid :list-invalid]
   [:set :set-valid :set-invalid]
   [:map :map-valid :map-invalid]])

(def libs
  [{:lib :spec :table spec-validators}
   {:lib :malli :table malli-validators}
   {:lib :spell :table spell-validators}])

(defn- assert-all-cases! []
  (doseq [[case-key _ _] cases
          {:keys [lib table]} libs]
    (when-not (contains? table case-key)
      (throw (ex-info (str "Missing validator: " lib " / " case-key)
                      {:lib lib :case case-key})))))

(defn- row
  [{:keys [lib]} case-key validity iters avg total]
  [(name lib) (name case-key)
   (name validity) iters avg total])

(defn -main [& _]
  (println "Start microbench…")
  (assert-all-cases!)
  (let [outfile (io/file "resources" "bench.csv")]
    (io/make-parents outfile)
    (with-open [w (io/writer outfile)]
      (csv/write-csv w [["lib" "case" "validity"
                         "iterations" "avg_ns" "total_ns"]])
      (let [iters (long 100000)]
        (doseq [[case-key valid-k invalid-k] cases
                {:keys [lib table]} libs
                :let [vf (get table case-key)
                      v-valid   (samples valid-k)
                      v-invalid (samples invalid-k)]
                validity [:valid :invalid]
                :let [v (if (= validity :valid) v-valid v-invalid)
                      {:keys [avg_ns total_ns iters]}
                      (bench-avg-ns #(vf v) iters)]]
          (csv/write-csv w [(row {:lib lib} case-key
                                 validity iters avg_ns total_ns)])))))
  (println "Done."))

(comment
  (-main {}))
