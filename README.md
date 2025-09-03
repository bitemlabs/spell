```
  _________             .__  .__
 /   _____/_____   ____ |  | |  |
 \_____  \\____ \_/ __ \|  | |  |
 /        \  |_> >  ___/|  |_|  |__
/_______  /   __/ \___  >____/____/
        \/|__|        \/
```

# spell

**spell** is a lightweight, flexible runtime validation library for Clojure. It's a drop-in replacement for `clojure.spec`, with fewer restrictions and more expressiveness — all without requiring macros everywhere.

## ✨ Highlights

- ✅ **Drop-in replacement for `spec`**
- 🚫 **Not like `malli`** — No external schema trees; you attach specs to values directly.
- 🔗 **Attach specs to more than just qualified keywords**:
  - ✅ Qualified or unqualified keywords
  - ✅ Strings
  - ✅ Vectors
- 🔧 **Minimal, expressive instrumentation via `defnt`**
- 🎚️ **Configurable instrumentation**:
  - `:high` — throws on invalid input/output
  - `:low` — prints validation info
  - `:none` — disables instrumentation
- ♻️ **Dynamic spec management** — You can add, update, or remove specs at runtime

---

## 📦 Defining and Using Specs

Use `s/def` to associate a spec with a keyword, string, or vector.  The
registration is mutable, so redefining a name updates the spec at runtime.
Once defined, validate values with `s/valid?`:

```clojure
(require '[spell.core :as s])

(s/def :int int?)
(s/def :string string?)

(s/valid? :int 42)        ;; => true
(s/valid? :int "oops")    ;; => false
```

Specs are not limited to keywords—you can also target strings and vectors:

```clojure
(s/def ["age"] pos-int?)
(s/valid? ["age"] 20)     ;; => true
```

### ✅ Map validation

```clojure
(s/def :a :int)
(s/def :b :int)

(s/valid? {:req [:a :b]} {:a 1 :b 2})           ;; => true
(s/valid? {:req [:a] :opt [:b]} {:a 1})         ;; => true
(s/valid? {:req [:a] :opt [:b]} {:b 2})         ;; => false
```

### 🔍 Logical operators

```clojure
(s/valid? [:or :int :string] 42)     ;; => true
(s/valid? [:or :int :string] "hi")   ;; => true
(s/valid? [:or :int :string] :foo)   ;; => false

(s/valid? [:and int? #(>= % 0)] 3)   ;; => true
(s/valid? [:and int? #(>= % 0)] -1)  ;; => false
```

### 🧺 Collections

```clojure
(s/valid? [:vector :int] [1 2 3])         ;; => true
(s/valid? [:vector :int] [1 "oops"])      ;; => false

(s/valid? [:list :string] '("a" "b"))     ;; => true
(s/valid? [:set :keyword] #{:a :b})       ;; => true
```

---

## 🔁 Instrumentation with `defnt`

Use `defnt` to define functions with validation on inputs and outputs.

```clojure
(s/defnt square
  [x]
  [:int :=> :int]
  (* x x))

(square 3)   ;; => 9
(square "x") ;; => throws (in :high mode)
```

Multi-arity is supported:

```clojure
(s/defnt sum
  ([a] [:int :=> :int] a)
  ([a b] [:int :int :=> :int] (+ a b)))

(sum 5)       ;; => 5
(sum 3 4)     ;; => 7
(sum 3 "x")   ;; => throws
```

### ClojureScript usage

When targeting ClojureScript, require macros separately:

```clojure
(ns my.app
  (:require [spell.core :as s]
            [spell.instrument :as inst])
  (:require-macros [spell.instrument :as inst]))

(inst/defnt square
  [x]
  [:int :=> :int]
  (* x x))
```

---

## 🎛️ Instrumentation Levels

Set the global instrumentation level:

```clojure
(s/inst!)   ;; high — throws on errors
(s/midst!)  ;; low  — prints errors only
(s/unst!)   ;; none — disables all instrumentation checking
```

---

## 🧪 Testing

Run the tests:

```bash
clojure -X:test
```

---

## 🧠 Design Philosophy

- Like `spec`, without the namespace-only restriction
- Keeps runtime overhead minimal
- Prioritizes clear, precise validation feedback
- Schema definitions are flexible and composable
