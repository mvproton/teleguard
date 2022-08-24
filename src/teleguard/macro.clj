(ns teleguard.macro)

(defmacro cond-some*
  ; https://gist.github.com/jack-r-warren/7bb870594646e7c98ac4de5f30b7e496
  "A combination of cond, if-some, and some common if-let* implementations online.
  Behaves like cond except the predicate is some number of bindings which are
  available in the second part. Example:
  (cond-some*
    [my-var-1 true my-var-2 nil] (println \"Won't reach here, any binding to nil
                                            makes the predicate false\")
    [a 2 b 3 c false] (println (+ a b)) ;; 5 is printed because false is still
                                        ;; non-nil so the predicate is true
    :else (println \"Symbols are always true, but we won't reach here because
                     the above case gets triggered\"))
  Cursive's IntelliJ plugin has a hard time understanding the binding syntax.
  Warnings for the first pair can be disabled by putting the cursor on the
  name above, pressing ⌥↩, and choosing the option to resolve as `let`."
  [& clauses]
  (let [bindings (first clauses) body (second clauses)]
    (if (and (not (keyword? bindings))
             (seq bindings))
      `(if-some [~(first bindings) ~(second bindings)]
         (cond-some* ~(drop 2 bindings) ~@(rest clauses))
         (cond-some* ~@(drop 2 clauses)))
      (do body))))