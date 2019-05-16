(ns kouta-indeksoija-service.queue.sqs
  (:require [amazonica.core :as amazonica]
            [amazonica.aws.sqs :as sqs]
            [kouta-indeksoija-service.util.conf :refer [env sqs-endpoint]])
  (:import (com.amazonaws.services.sqs.model QueueDoesNotExistException)))

(defn- with-endpoint
  [f]
  (if (not (clojure.string/blank? sqs-endpoint))
    (amazonica/with-credential {:endpoint sqs-endpoint} (f))
    (f)))

(defn find-queue
  [name]
  (if-let [q (with-endpoint #(sqs/find-queue name))]
    q
    (throw (QueueDoesNotExistException. (str "No queue '" name "' found")))))


(defn delete-message
  [& {:keys [queue-url receipt-handle]}]
  (with-endpoint #(sqs/delete-message
                    :queue-url queue-url
                    :receipt-handle receipt-handle)))

(defn long-poll
  [queue]
  (with-endpoint #(sqs/receive-message
                    :queue-url queue
                    :max-number-of-messages 10
                    :delete false
                    :wait-time-seconds 20)))

(defn short-poll
  [queue]
  (with-endpoint #(sqs/receive-message
                    :queue-url queue
                    :max-number-of-messages 10
                    :delete false)))