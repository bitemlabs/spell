![spell-logo](./resources/images/spell-logo.png)

**spell** is a lightweight, flexible runtime validation library for Clojure. It's a drop-in replacement for `clojure.spec`, with fewer restrictions and more expressiveness — all without requiring macros everywhere.

## ✨ Highlights

- ✅ **Drop-in replacement for `spec`**
- 🚫 **Not like `malli`** — No external schema trees; you attach specs to values directly.
- 🔗 **Attach specs to more than just qualified keywords**:
  - ✅ Qualified keywords (recommended, `clojure.spec`-style)
  - ✅ Unqualified keywords
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

Like `clojure.spec`, the primary way to define specs is with **qualified keywords**.  

```clojure
(require '[spell.core :as s])

(s/define :my.app/int int?)
(s/define :my.app/string string?)

(s/valid? :my.app/int 42)       ;; => true
(s/valid? :my.app/int "oops")   ;; => false
```

### Alternative forms

While qualified keywords are the idiomatic choice, Spell also lets you attach specs to other identifiers when desired:

```clojure
;; Unqualified keyword
(s/define :int int?)
(s/valid? :int 42)             ;; => true

;; String
(s/define ["age"] pos-int?)
(s/valid? ["age"] 20)          ;; => true

;; Vector
(s/define [:coords] [:vector int?])
(s/valid? [:coords] [1 2 3])   ;; => true
```

Rarely, but if you want to remove a spec:

```clojure
(s/undef :my.app/int)
```

---

### ✅ Map validation

```clojure
(s/define :my.app/a :int)
(s/define :my.app/b :int)

(s/valid? {:req [:my.app/a :my.app/b]} {:my.app/a 1 :my.app/b 2})
;; => true

(s/valid? {:req [:my.app/a] :opt [:my.app/b]} {:my.app/a 1})
;; => true

(s/valid? {:req [:my.app/a] :opt [:my.app/b]} {:my.app/b 2})
;; => false
```

---

### 🔍 Logical operators

```clojure
(s/valid? [:or :my.app/int :my.app/string] 42)   ;; => true
(s/valid? [:or :my.app/int :my.app/string] "hi") ;; => true
(s/valid? [:or :my.app/int :my.app/string] :foo) ;; => false

(s/valid? [:and int? #(>= % 0)] 3)   ;; => true
(s/valid? [:and int? #(>= % 0)] -1)  ;; => false
```

---

### 🧺 Collections

```clojure
(s/valid? [:vector :my.app/int] [1 2 3])      ;; => true
(s/valid? [:vector :my.app/int] [1 "oops"])   ;; => false

(s/valid? [:list :my.app/string] '("a" "b"))  ;; => true
(s/valid? [:set :my.app/keyword] #{:a :b})    ;; => true
```

---

## 🔁 Instrumentation with `tdefn`

Use `tdefn` to define functions with validation on inputs and outputs.

```clojure
(s/tdefn square
  [x]
  [:my.app/int :=> :my.app/int]
  (* x x))

(square 3)   ;; => 9
(square "x") ;; => throws (in :high mode)
```

Multi-arity is supported:

```clojure
(s/tdefn sum
  ([a] [:my.app/int :=> :my.app/int] a)
  ([a b] [:my.app/int :my.app/int :=> :my.app/int] (+ a b)))

(sum 5)       ;; => 5
(sum 3 4)     ;; => 7
(sum 3 "x")   ;; => throws
```

---

## Validate local bindings with `tlet`

`tlet` is a `let`-style macro that validates each binding against a spec **as it’s bound**.  
This works seamlessly with qualified keywords, but also supports all other forms (unqualified keywords, strings, vectors, maps, etc.).

```clojure
(s/define :my.app/int int?)
(s/define ["age"] pos-int?)

(s/tlet [x :my.app/int 9
         y ["age"] 20]
  [x y])
;; => [9 20]
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
clj -X:test
```

---

## 🧠 Design Philosophy

- Encourage **qualified keywords** for clarity and namespacing (like `spec`)
- Allow **unqualified keywords, strings, or vectors** when convenience matters
- Keep runtime overhead minimal
- Prioritize clear, precise validation feedback
- Schema definitions are flexible and composable
