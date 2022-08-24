(ns teleguard.server
  (:require
    [aleph.http :as http]
    [clojure.tools.logging :as log]
    [reitit.http :as rhttp]
    [reitit.interceptor.sieppari :as sieppari]
    [sieppari.async.manifold]
    [muuntaja.interceptor :as mi]
    [reitit.http.interceptors.parameters :as rp])
  (:import (java.net InetSocketAddress)))


(defn default-handler [req]
  (log/trace "Not found handler: " (pr-str req))
  {:status 404 :body "Not found ;("})

(defn create-handler [routes]
  (rhttp/ring-handler
    (rhttp/router routes)
    default-handler
    {:executor     sieppari/executor
     :interceptors [(rp/parameters-interceptor)
                    (mi/format-interceptor)]}))

(defn start! [routes {:keys [host port]}]
  (let [app-handler (create-handler routes)
        socket-addr (InetSocketAddress. ^String host ^Integer port)
        server      (http/start-server app-handler {:epoll?         true
                                                    :socket-address socket-addr})]
    (log/info "HTTP server was started!")
    (atom server)))

(defn stop! [server]
  (when (some? @server)
    (.close @server)
    (log/info "HTTP server was stopped.")
    (reset! server nil)))
