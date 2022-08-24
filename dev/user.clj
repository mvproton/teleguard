(ns user
  (:refer-clojure :exclude [reset!])
  (:require
    [clojure.tools.namespace.repl :refer [refresh-all]]
    [teleguard.core :as core]))

(def +dev-config+ "dev/config-example.edn")
(defonce system nil)


(defn go! []
  (let [cfg    (core/init! +dev-config+)
        system (core/start! cfg)]
    (alter-var-root #'system (constantly system))))

(defn stop! []
  (core/stop! system))

(defn reset! []
  (stop!)
  (refresh-all :after 'user/go!))

(comment

  )