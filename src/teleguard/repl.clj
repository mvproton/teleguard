(ns teleguard.repl
  (:require
    [clojure.tools.logging :as log]
    [nrepl.server :as srv]))

(defn start! [{:keys [host port]}]
  (let [server (srv/start-server :bind host :port port)]
    (log/info "nREPL server was started!")
    (atom server)))

(defn stop! [server]
  (srv/stop-server @server)
  (log/info "nREPL server was stopped.")
  (reset! server nil))
