(ns kouta-indeksoija-service.queue.notification-queue
  (:require [clojure.tools.logging :as log]
            [clj-log.error-log :refer [with-error-logging]]
            [kouta-indeksoija-service.queue.sqs :as sqs]
            [kouta-indeksoija-service.queue.queue :as queue]
            [kouta-indeksoija-service.notifier.notifier :refer [send-notification]]
            [kouta-indeksoija-service.util.tools :refer [get-id]])
  (:import (software.amazon.awssdk.services.sqs.model QueueDoesNotExistException)))

(defn receive-messages-from-notification-queue
  []
  (let [q (sqs/queue :notifications)
        res (sqs/long-poll q)
        messages (:messages res)]
    (map #(assoc % :queue q) messages)))

(defn handle-messages-from-queues
  "Receive messages from queues and call handler function on them. Delete the successfully
  handled messages from the queue."
  ([handler] (handle-messages-from-queues handler queue/body-json->map))
  ([handler unwrapper]
   (let [messages (receive-messages-from-notification-queue)]
     (doseq [message messages]
       (let [unwrapped (unwrapper message)]
         (try
           (handler unwrapped)
           (sqs/delete-message :queue-url (:queue message)
                               :receipt-handle (:receipt-handle message))
           (catch Exception e
             (log/warn "Failed sending notification message to"
                       (:receiver unwrapped)
                       "for"
                       (:type (:message unwrapped))
                       (get-id (:message unwrapped))
                       "because"
                       (.getMessage e)))))))))

(defn read-and-send-notifications
  []
  (log/info "Start listening on notification queue.")
  (loop []
    (try
      (handle-messages-from-queues
       (fn [message]
         (send-notification message)))
      (catch QueueDoesNotExistException e
        (log/error e "Notification queue does not exist. Sleeping for 30 seconds and continue polling.")
        (Thread/sleep 30000))
      (catch Exception e
        (log/error e "Error in reading notification messages from the queue. Sleeping for 3 seconds and continue polling.")
        (Thread/sleep 3000)))
    (recur)))

