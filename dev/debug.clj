(ns debug
  (:require [manifold.deferred :as d]))

(defn dbg! [d]
  (-> d
      (d/chain'
        (fn [res]
          (println "Response: \n" (with-out-str (clojure.pprint/pprint res)))))
      (d/catch'
        (fn [ex]
          (println "Error: " ex)))))