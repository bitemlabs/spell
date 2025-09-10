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
- 🔧 **Minimal, expressive instrumentation via `tdefn`**
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

## 🔁 Instrumentation with `tdefn`

Use `tdefn` to define functions with validation on inputs and outputs.

```clojure
(s/tdefn square
  [x]
  [:int :=> :int]
  (* x x))

(square 3)   ;; => 9
(square "x") ;; => throws (in :high mode)
```

Multi-arity is supported:

```clojure
(s/tdefn sum
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

(inst/tdefn square
  [x]
  [:int :=> :int]
  (* x x))
```

---

## Validate local bindings with `tlet`

`tlet` is a `let`-style macro that validates each binding against a spec **as it’s bound**. If any binding fails under strict instrumentation, evaluation short-circuits before the body runs. It’s a lightweight way to assert invariants for intermediate values, pipelines, or destructured data.

**Syntax**

```clj
(s/tlet [binding-form  spec  value
         binding-form2 spec2 value2
         ...]
  ;; body that can use the validated locals
  ...)
```

- `binding-form` can be a symbol or a destructuring form (vector/map), just like `let`.
- `spec` can be **anything you’d pass to `s/valid?`**: keywords/strings/vectors you’ve defined via `s/def`, map specs with `:req`/`:opt`, logical forms like `[:or ...]` / `[:and ...]`, or collection specs like `[:vector :int]`.

### Examples

**Single binding**

```clj
(require '[spell.core :as s])

(s/def :int int?)

(s/tlet [x :int 9]
  (+ x 1))
;; => 10  (valid)
```

**Chained bindings (later bindings can reference earlier ones)**

```clj
(s/tlet [a :int 3
         b :int (+ a 4)]
  (* a b))
;; => 21
```

**Vector destructuring + collection spec**

```clj
(s/tlet [[x y] [:vector :int] [2 5]]
  (- y x))
;; => 3
```

**Map destructuring + map spec**

```clj
(s/def :a :int)
(s/def :b :int)

(s/tlet [{:keys [a b]} {:req [:a :b]} {:a 2 :b 8}]
  (/ b a))
;; => 4
```

**Logical and string-named specs**

```clj
(s/def ["age"] pos-int?)

(s/tlet [age ["age"] 20
         s   [:or :string :int] "ok"]
  (str age "-" s))
;; => "20-ok"
```

### Instrumentation behavior

`tlet` respects the global instrumentation level you set elsewhere in Spell:

```clj
(s/inst!)   ;; :high — throws on errors
(s/midst!)  ;; :low  — prints validation info
(s/unst!)   ;; :none — disables checks
```

| Level   | On invalid binding                          | Body runs? |
|---------|---------------------------------------------|------------|
| `:high` | Throws with detailed context                | No         |
| `:low`  | Prints validation details, returns the body | Yes        |
| `:none` | Skips validation entirely                   | Yes        |

> Tip: Because validation happens **per binding**, failures are reported at the exact binding that didn’t conform, which makes debugging fast and local.

### ClojureScript usage

Just like `tdefn`, use the macro from `spell.instrument` when compiling to CLJS:

```clj
(ns my.app
  (:require [spell.core :as s]
            [spell.instrument :as inst])
  (:require-macros [spell.instrument :as inst]))

(inst/tlet [x :int 5
            y :int (+ x 1)]
  [x y])
;; => [5 6]
```

(For Clojure/JVM you can call `s/tlet` directly; for CLJS, require the macro namespace as above—same pattern as in the `tdefn` example.)

### Gotchas

- The binding vector must be in **triples**: `binding-form spec value`. A malformed binding vector will raise an error.
- Your specs must already be registered (e.g., via `s/def`) if you use named specs like `:int` or `["age"]`.

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
clj -X:test
```

---

## 🧠 Design Philosophy

- Like `spec`, without the namespace-only restriction
- Keeps runtime overhead minimal
- Prioritizes clear, precise validation feedback
- Schema definitions are flexible and composable
