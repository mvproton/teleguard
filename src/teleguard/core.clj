(ns teleguard.core
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [clojure.edn :as edn]
    [teleguard.webhook-routes :as whr]
    [teleguard.bot :as bot]
    [teleguard.server :as srv]
    [teleguard.repl :as repl]))

(defonce *system nil)

(defn init! [cfg-path]
  (-> cfg-path io/file slurp edn/read-string))


(defn start! [{:keys [webhook-url token channel-user-name server-opts repl] :as config}]
  (let [webhook (whr/start! 100)
        server  (srv/start! (:routes @webhook) server-opts)
        bot     (bot/start! (:event-stream @webhook) {:token             token
                                                      :webhook-url       webhook-url
                                                      :channel-user-name channel-user-name})
        repl    (repl/start! repl)]
    (atom {:webhook webhook :server server :bot bot :repl repl})))


(defn stop! [system]
  (when (some? system)
    (let [{:keys [webhook server bot repl]} @system]
      (when (some? webhook)
        (whr/stop! webhook))
      (when (some? server)
        (srv/stop! server))
      (when (some? bot)
        (bot/stop! bot))
      (when (some? repl)
        (repl/stop! repl))
      (reset! system nil))))


(defn add-shutdown-hook! [hook]
  (let [r (Runtime/getRuntime)]
    (.addShutdownHook ^Runtime r (Thread. ^Runnable hook))))


(defn -main [& args]
  (if-some [file-path (get (apply hash-map args) "--config")]
    (try
      (let [cfg    (init! file-path)
            system (start! cfg)]
        (alter-var-root #'*system (constantly system))
        (add-shutdown-hook!
          (fn []
            (log/info "Shutdown hook. Trying to release resources...")
            (try (stop! system)
                 (catch Exception ex
                   (log/error "Shutdown error: " ex))))))
      (catch Exception ex
        (log/error "Starting error: " ex)))
    (println "Arguments error. Usage: java -jar teleguard.jar --config config.edn")))