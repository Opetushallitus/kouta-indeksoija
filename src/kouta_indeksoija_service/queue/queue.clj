(ns kouta-indeksoija-service.queue.queue
  (:require [clojure.tools.logging :as log]
            [clj-log.error-log :refer [with-error-logging]]
            [cheshire.core :as json]
            [clojure.core.reducers :as r]
            [kouta-indeksoija-service.util.conf :refer [env]]
            [kouta-indeksoija-service.queue.sqs :as sqs]
            [kouta-indeksoija-service.queue.state :as state]
            [kouta-indeksoija-service.util.collections :as coll]
            [kouta-indeksoija-service.kouta.indexer :as indexer])
  (:import (com.amazonaws.services.sqs.model QueueDoesNotExistException)))


(defn combine-messages
  [messages]
  (r/fold
    (constantly {})
    (fn [x y] (merge-with (fn [a b] (distinct (concat a b))) x y))
    messages))

(defn queue
  [priority]
  (sqs/find-queue (get (:queue env) priority)))

(defn receive
  [{:keys [q f]}]
  (assoc (f q) :queue q))

(defn receive-messages-from-queues
  []
  (coll/collect-first
    receive
    #(seq (:messages %))
    [{:q (queue :priority) :f sqs/long-poll}
     {:q (queue :fast) :f sqs/short-poll}
     {:q (queue :slow) :f sqs/short-poll}]))


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


(defn index-from-queue!
  "Start future to receive messages from queues and index them. On errors just
  prints log message and continues receiving."
  []
  (future
    (log/info "Start listening on queues.")
    (loop []
      (try
        (handle-messages-from-queues
          (fn
            [messages]
            (doseq [step [#(state/set-states! ::state/started %)
                          #(indexer/index-oids (combine-messages %))
                          #(state/set-states! ::state/indexed %)]]
              (step messages))))
        (catch QueueDoesNotExistException e
          (log/error e "Queues do not exist. Sleeping for 30 seconds and continue polling.")
          (Thread/sleep 30000))
        (catch Exception e
          (log/error e "Error in receiving indexing messages. Sleeping for 3 seconds and continue polling.")
          (Thread/sleep 3000)))
      (recur))
    (log/warn "Stopped listening on queues.")))


(defn handle-failed
  "Handle messages from DLQ. Mark message states to failed."
  []
  (if-let [dlq (queue :dlq)]
    (when-let [failed (seq (:messages (sqs/short-poll dlq)))]
      (do
        (state/set-states! ::state/failed failed)
        (doseq [msg failed] (sqs/delete-message :queue-url dlq :receipt-handle (:receipt-handle msg)))))
    (log/error "No DLQ found.")))
