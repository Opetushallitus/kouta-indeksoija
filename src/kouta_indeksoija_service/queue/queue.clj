(ns kouta-indeksoija-service.queue.queue
  (:require [clojure.tools.logging :as log]
            [clj-log.error-log :refer [with-error-logging]]
            [cheshire.core :as json]
            [clojure.core.reducers :as r]
            [kouta-indeksoija-service.queue.sqs :as sqs]
            [kouta-indeksoija-service.queue.state :as state]
            [kouta-indeksoija-service.indexer.indexer :as indexer]
            [kouta-indeksoija-service.notifier.notifier :as notifier])
  (:import (software.amazon.awssdk.services.sqs.model QueueDoesNotExistException)))


(defn combine-messages
  [messages]
  (r/fold
    (constantly {})
    (fn [x y] (merge-with (fn [a b] (distinct (concat a b))) x y))
    messages))

(defn receive
  [{:keys [q f]}]
  (assoc (f q) :queue q))

(defn collect-first
  "get first mapped value that matches 'check?' or nil"
  ([f check? seq]
   (loop [values seq]
     (when (not-empty values)
       (let [current (first values)
             mapped (f current)]
         (if (check? mapped)
           mapped
           (recur (rest values))))))))

(defn receive-messages-from-queues
  []
  (collect-first
    receive
    #(seq (:messages %))
    [{:q (sqs/queue :priority) :f sqs/long-poll}
     {:q (sqs/queue :fast) :f sqs/short-poll}
     {:q (sqs/queue :slow) :f sqs/short-poll}]))

(defn body-json->map
  [msg]
  (json/parse-string (:body msg) true))

(defn handle-messages-from-queues
  "Receive messages from queues and call handler function on them. If handling is
  successful delete messages from queue."
  ([handler] (handle-messages-from-queues handler body-json->map))
  ([handler unwrap]
   (let [received (receive-messages-from-queues)
         messages (:messages received)]
     (when (seq messages)
       (handler (map unwrap messages))
       (doseq
         [msg (map :receipt-handle messages)]
         (sqs/delete-message :queue-url (:queue received)
                             :receipt-handle msg))))))

(defn handle-messages
  [messages]
  (doseq [step (let [execution-id (. System (currentTimeMillis))]
                 [#(state/set-states! ::state/started % execution-id)
                  #(notifier/notify (indexer/index-oids (combine-messages %) execution-id))
                  #(state/set-states! ::state/indexed % execution-id)])]
    (step messages)))

(defn index-from-sqs
  []
  (log/info "Start listening on queues.")
  (loop []
    (try (handle-messages-from-queues handle-messages)
      (catch QueueDoesNotExistException e
        (log/error e "Queues do not exist. Sleeping for 30 seconds and continue polling.")
        (Thread/sleep 30000))
      (catch Exception e
        (log/error e "Error in receiving indexing messages. Sleeping for 3 seconds and continue polling.")
        (Thread/sleep 3000)))
    (recur)))

