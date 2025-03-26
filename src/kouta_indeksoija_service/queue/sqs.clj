(ns kouta-indeksoija-service.queue.sqs
  (:require [kouta-indeksoija-service.queue.conf :as conf]
            [clojure.string :refer [blank?]]
            [cheshire.core :refer [generate-string]])
  (:import (java.net URI)
           (software.amazon.awssdk.auth.credentials AwsCredentialsProviderChain
                                                    ContainerCredentialsProvider
                                                    DefaultCredentialsProvider
                                                    EnvironmentVariableCredentialsProvider
                                                    StaticCredentialsProvider
                                                    AwsBasicCredentials)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.sqs SqsClient)
           (software.amazon.awssdk.services.sqs.model ListQueuesRequest
                                                      QueueDoesNotExistException
                                                      DeleteMessageRequest
                                                      ReceiveMessageRequest
                                                      SendMessageRequest
                                                      GetQueueAttributesRequest
                                                      PurgeQueueRequest
                                                      Message)))

(def long-poll-wait-time    20)
(def max-number-of-messages 10)

(def credentialsProvider
  (let [default-providers [(DefaultCredentialsProvider/create)
                           (ContainerCredentialsProvider/create)
                           (EnvironmentVariableCredentialsProvider/create)]]
    (-> (AwsCredentialsProviderChain/builder)
        (.credentialsProviders (if conf/test?
                                 (assoc default-providers
                                        3
                                        (StaticCredentialsProvider/create
                                          (AwsBasicCredentials/create
                                            conf/localstack-aws-access-key-id
                                            conf/localstack-aws-secret-access-key)))
                                 default-providers))
        (.build))))

(defn- with-client
  ^SqsClient
  [f]
  (let [sqs-client (-> (SqsClient/builder)
                       (.region (Region/of conf/sqs-region))
                       (.endpointOverride (when conf/sqs-endpoint
                                            (URI. conf/sqs-endpoint)))
                       (.credentialsProvider credentialsProvider)
                       (.build))]
    (f sqs-client)))

(defn- find-queue
  [name]
  ; sqs/find-queue ei löydä jonoa oikeaa "notifications":lle, koska "notifications-dlq" sisältyy siihen.
  ; Toteutetaan siis jonon hakeminen tarkalla nimellä.
  (let [qs (with-client (fn [client]
                          (-> client
                              (.listQueues (-> (ListQueuesRequest/builder)
                                               (.build)))
                              (.queueUrls))))]
    (if-let [q (first (filter #(.endsWith % (str "/" name)) qs))]
      q
      (throw (-> (QueueDoesNotExistException/builder)
                 (.message (str "No queue '" name "' found"))
                 (.build))))))

(defn queue
  [priority]
  (find-queue (conf/name priority)))

(defn delete-message
  [& {:keys [queue-url receipt-handle]}]
  (with-client (fn [client]
                 (-> client
                     (.deleteMessage (-> (DeleteMessageRequest/builder)
                                         (.queueUrl queue-url)
                                         (.receiptHandle receipt-handle)
                                         (.build)))))))

(defn ->sqs-message
  [^Message message]
  {:body (.body message)
   :receipt-handle (.receiptHandle message)})

(defn- ->sqs-messages
  [messages]
  (map ->sqs-message messages))

(defn long-poll
  [queue]
  (with-client (fn [client]
                 {:messages
                  (-> client
                      (.receiveMessage (-> (ReceiveMessageRequest/builder)
                                           (.queueUrl queue)
                                           (.maxNumberOfMessages (int max-number-of-messages))
                                           (.waitTimeSeconds (int long-poll-wait-time))
                                           (.build)))
                      (.messages)
                      (->sqs-messages))})))

(defn short-poll
  [queue]
  (with-client (fn [client]
                 {:messages
                  (-> client
                      (.receiveMessage (-> (ReceiveMessageRequest/builder)
                                           (.queueUrl queue)
                                           (.maxNumberOfMessages (int max-number-of-messages))
                                           (.build)))
                      (.messages)
                      (->sqs-messages))})))

(defn send-message
  [queue message]
  (let [true-message (if (string? message) message (generate-string message))]
    (with-client (fn [client]
                   (-> client
                       (.sendMessage (-> (SendMessageRequest/builder)
                                         (.queueUrl queue)
                                         (.messageBody true-message)
                                         (.build))))))))

(defn get-queue-attributes
  [priority & attr]
  (with-client (fn [client]
                 (-> client
                     (.getQueueAttributes (-> (GetQueueAttributesRequest/builder)
                                              (.queueUrl (queue priority))
                                              (.attributeNames (vec attr))
                                              (.build)))
                     (.attributes)))))

(defn purge-queue
  [queue]
  (with-client (fn [client]
                 (-> client
                     (.purgeQueue (-> (PurgeQueueRequest/builder)
                                      (.queueUrl queue)
                                      (.build)))))))