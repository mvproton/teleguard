(ns teleguard.webhook-routes
  (:require
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [clojure.tools.logging :as log]))


(defn webhook-handler [req stream]
  (let [body (:body-params req)]
    (-> (s/put! stream body)
        (d/chain'
          (fn [status]
            {:status 200 :body {:status :ok}})))))

(defn create-routes [stream]
  [["/teleguard-webhook" {:post #(webhook-handler % stream)}]])

(defn start! [buffer-size]
  (let [s      (s/stream buffer-size)
        routes (create-routes s)]
    (log/info "Webhook stream was started!")
    (atom {:routes routes :event-stream s})))

(defn stop! [webhook]
  (let [stream (:event-stream @webhook)]
    (s/close! stream)
    (log/info "Webhook stream was stopped.")
    (reset! webhook nil)))


