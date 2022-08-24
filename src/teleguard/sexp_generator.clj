(ns teleguard.sexp-generator)

(def +ops+ ['+ '- '*])
(def +max-scalar+ 15)
(def -min-list-size- 2)
(def +max-list-size+ 3)
; [0.0; 1.0)
(def +sexp-probability+ 0.3)

(declare gen-sexp)


(defn gen-scalar []
  (rand-int +max-scalar+))


(defn gen-list-size []
  (+ -min-list-size-
     (rand-int (inc (- +max-list-size+ -min-list-size-)))))


(defn gen-list [level]
  (let [count (gen-list-size)]
    (repeatedly
      count
      (fn []
        (if (and (< (rand) +sexp-probability+) (pos? level))
          (gen-sexp (dec level))
          (gen-scalar))))))


(defn gen-sexp [level]
  (let [op   (rand-nth +ops+)
        list (gen-list level)]
    (cons op list)))