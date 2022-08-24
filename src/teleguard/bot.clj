(ns teleguard.bot
  (:require
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [manifold.time :as mt]
    [clojure.tools.logging :as log]
    [clojure.string :as str]
    [teleguard.telegram-api :as tapi]
    [teleguard.sexp-generator :as sgen]
    [teleguard.macro :refer [cond-some*]])
  (:import (java.time Duration LocalDateTime ZoneId)))


(def +registration-timeout+ (-> 60 mt/seconds))
(def +max-retry-count+ 3)
(def +ban-duration+ (-> 1 Duration/ofMinutes))
(defonce unauthorized-users (atom {}))
(def +permissions+ #{:can_send_other_messages
                     :can_send_media_messages
                     :can_send_messages
                     :can_invite_users
                     :can_pin_messages
                     :can_add_web_page_previews
                     :can_change_info
                     :can_send_polls})


(defn interval->timestamp! [^Duration duration]
  (let [zone-id (ZoneId/systemDefault)]
    (-> (LocalDateTime/now)
        (.plus duration)
        (.atZone zone-id)
        .toEpochSecond)))


(defn permissions-off []
  (zipmap +permissions+ (repeat false)))

(defn permissions-on []
  (zipmap +permissions+ (repeat true)))


(defn message->event' [msg]
  (if-some [msg' (:message msg)]
    (let [msg-id (:message_id msg')]
      (cond-some*
        [chat-member (get-in msg' [:new_chat_members 0])]
        (let [{:keys [username id]} chat-member]
          {:type :message/user-joined :message-id msg-id :user-id id :username username})

        [chat-member (:left_chat_member msg')]
        (let [{:keys [username id]} chat-member]
          {:type :message/user-left :message-id msg-id :user-id id :username username})

        :else
        {:type    :message/regular :message-id msg-id
         :user-id (get-in msg' [:from :id])
         :text    (str/trim (:text msg'))}))
    {:type :unknown :data msg}))


(defmulti handle-event (fn [evt bot-ctx] (:type evt)))
(derive :chat-member/left :chat-member/no-longer-in-chat)
(derive :chat-member/kicked :chat-member/no-longer-in-chat)


(defn clear-state! [user-id {:keys [token channel-user-name] :as bot-ctx}]
  (log/trace "Clearing state for user: " user-id)
  (when-some [{:keys [message-id timeout-d]} (@unauthorized-users user-id)]
    (tapi/log-api-error
      (tapi/delete-message token {:chat_id channel-user-name :message_id message-id}))
    (d/success! timeout-d :cancel)
    (swap! unauthorized-users dissoc user-id)))


(defmethod handle-event :message/regular [evt {:keys [token channel-user-name] :as bot-ctx}]
  (let [{:keys [user-id text message-id]} evt]
    (when-some [{:keys [answer retry]} (@unauthorized-users user-id)]
      (tapi/log-api-error
        (tapi/delete-message token {:chat_id channel-user-name :message_id message-id}))
      (if (= (str answer) text)
        (do (log/info "User " user-id "joined to chat.")
            (clear-state! user-id bot-ctx)
            (tapi/log-api-error
              (tapi/restrict-chat-member token {:chat_id     channel-user-name :user_id user-id
                                                :permissions (permissions-on)})))
        (if (> retry 1)
          (swap! unauthorized-users update-in [user-id :retry] dec)
          (let [ts (interval->timestamp! +ban-duration+)]
            (tapi/log-api-error
              (tapi/ban-chat-member token {:chat_id    channel-user-name :user_id user-id
                                           :until_date ts}))))))))


(defmethod handle-event :message/user-joined [{msg-id :message-id :keys [user-id username] :as evt}
                                              {:keys [token channel-user-name] :as bot-ctx}]
  (let [question (sgen/gen-sexp 1)
        answer   (eval question)]
    (tapi/log-api-error
      (tapi/delete-message token {:chat_id channel-user-name :message_id msg-id}))
    (tapi/log-api-error
      (tapi/restrict-chat-member token {:chat_id     channel-user-name :user_id user-id
                                        :permissions (assoc (permissions-off) :can_send_messages true)}))
    (-> (tapi/send-message token {:chat_id channel-user-name :parse_mode "MarkdownV2"
                                  :text
                                  (str "Hello, @" username " \\! Please answer to the question: \n"
                                       "```\n"
                                       question
                                       "\n```")})
        (d/chain
          (fn [msg-res]
            (let [msg-id    (:message_id msg-res)
                  timeout-d (mt/in +registration-timeout+
                                   (fn []
                                     (tapi/log-api-error
                                       (tapi/ban-chat-member
                                         token
                                         {:chat_id    channel-user-name
                                          :user_id    user-id
                                          :until_date (interval->timestamp! (-> 35 Duration/ofSeconds))}))))]
              (swap! unauthorized-users assoc user-id {:answer     answer
                                                       :retry      +max-retry-count+
                                                       :message-id msg-id
                                                       :timeout-d  timeout-d}))))
        tapi/log-api-error)))


(defmethod handle-event :message/user-left [{:keys [user-id message-id] :as evt}
                                            {:keys [token channel-user-name] :as bot-ctx}]
  (clear-state! user-id bot-ctx)
  (tapi/log-api-error
    (tapi/delete-message token {:chat_id channel-user-name :message_id message-id})))


(defmethod handle-event :default [evt bot-ctx]
  (println "Unimplemented!" (pr-str evt)))


(defn !dbg-print-msg [msg]
  (println "Raw msg: \n"
           (with-out-str
             (clojure.pprint/pprint msg)))
  msg)


(defn process! [evt-stream bot-ctx]
  (->> evt-stream
       (s/filter some?)
       ;(s/map !dbg-print-msg)
       (s/map message->event')
       (s/consume-async
         (fn [evt]
           (clojure.pprint/pprint evt)
           (handle-event evt bot-ctx)
           (d/success-deferred true)))))


(defn start! [event-stream {:keys [token webhook-url] :as bot-ctx}]
  (-> (tapi/set-webhook token {:url             webhook-url
                               :allowed_updates ["message"]})
      (d/chain' (fn [res] (log/info "Webhook was set!")))
      (d/catch' (fn [ex] (log/error "Cannot set webhook. Error: " ex))))
  (process! event-stream bot-ctx)
  (atom {:token token}))


(defn stop! [processor]
  (let [{:keys [token]} @processor]
    (-> (tapi/delete-webhook token {:drop_pending_updates :true})
        (d/chain' (fn [_] (log/info "Webhook was deleted.")))
        (d/catch' (fn [ex] (log/error "Cannot delete webhook. Error: " ex))))
    (reset! processor nil)))
