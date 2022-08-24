(ns teleguard.telegram-api
  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [clj-commons.byte-streams :as bs]
            [jsonista.core :as j]
            [clojure.tools.logging :as log])
  (:import (clojure.lang ExceptionInfo)))

(defn method-url [token method]
  (str "https://api.telegram.org/bot" token "/" method))

(defn response->json [res]
  (-> res :body bs/to-string (j/read-value j/keyword-keys-object-mapper)))


(defn ok->result [msg]
  (if (true? (:ok msg))
    (:result msg)
    (d/error-deferred msg)))

(defn api-request
  ([token method params]
   (let [url (method-url token method)]
     (-> (if (empty? params)
           (http/post url)
           (let [json-data (j/write-value-as-string params j/keyword-keys-object-mapper)]
             (http/post url {:body         json-data
                             :content-type :json})))
         (d/chain' response->json ok->result)
         (d/catch' ExceptionInfo
           (fn [ex] (-> (ex-data ex) response->json d/error-deferred))))))
  ([token method]
   (api-request token method {})))

(defn log-api-error [d]
  (d/catch d (fn [ex] (log/error "API ERROR: " ex))))


(defn get-me [token]
  (api-request token "getMe"))


(defn get-webhook-info [token]
  (api-request token "getWebhookInfo"))


(defn set-webhook [token {:keys [url allowed_updates] :as params}]
  (api-request token "setWebhook" (cond-> params
                                          (nil? (:allowed_updates params))
                                          (assoc :allowed_updates []))))


(defn delete-webhook [token & [params]]
  (api-request token "deleteWebhook" params))


(defn get-chat [token {:keys [chat_id] :as params}]
  (api-request token "getChat" params))


(defn send-message [token {:keys [chat_id text] :as params}]
  (api-request token "sendMessage" params))


(defn restrict-chat-member [token {:keys [chat_id user_id permissions] :as params}]
  (api-request token "restrictChatMember" params))


(defn get-chat-member [token {:keys [chat_id user_id] :as params}]
  (api-request token "getChatMember" params))


(defn delete-message [token {:keys [chat_id message_id] :as params}]
  (api-request token "deleteMessage" params))


(defn ban-chat-member [token {:keys [chat_id user_id] :as params}]
  (api-request token "banChatMember" params))


(comment

  (def token (:token @(:processor @user/system)))

  (user/dbg! (get-webhook-info (:token @(:processor @user/system))))

  (user/debug! (get-chat-member token {:chat_id -1001617240797 :user_id 163930344}))

  )