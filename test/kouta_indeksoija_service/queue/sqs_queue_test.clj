(ns kouta-indeksoija-service.queue.sqs-queue-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [kouta-indeksoija-service.util.conf :refer [env]]
            [kouta-indeksoija-service.queue.queue :refer :all]
            [clj-test-utils.generic :refer [run-proc]]
            [kouta-indeksoija-service.queue.sqs :refer [queue delete-message send-message purge-queue]]
            [kouta-indeksoija-service.test-tools :refer [contains-same-elements-in-any-order?]]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.queue.notification-queue :as notification-queue]))


(use-fixtures :once (fn [t] (fixture/restart-elasticsearch t)))

(defonce test-long-poll-time 5)

;; (def localstack-image (. DockerImageName (parse "localstack/localstack:2.3.0")))
;; (def localstack-container (delay (-> (LocalStackContainer. localstack-image)
;;                                      (.withServices (into-array [org.testcontainers.containers.localstack.LocalStackContainer$Service/SQS])))))

(defn- message-bodies [response] (seq (map #(:body %) (:messages response))))

(defn- empty-queues [] (doseq [q (map queue (keys (:queue env)))] (purge-queue q)))

(defn localstack-fixture
  [tests]
  (run-proc "./tools/start_localstack")
  (tests)
  (run-proc "./tools/stop_localstack"))

(use-fixtures :once localstack-fixture)

(defmacro testing-with-queues-fixture
  [name & test]
  `(testing ~name
     (do
       ~@test
       (empty-queues))))

(deftest docker-sqs-test
  (with-redefs [kouta-indeksoija-service.queue.sqs/long-poll-wait-time test-long-poll-time]
    (let [expected-messages     [(json/generate-string {:oid ["expected-123.123.123" "expected-123.123.231"] :boid ["expected-123.123"]})
                                 (json/generate-string {:oid ["expected-234.234.234" "expected-234-234-123"] :droid ["expected-234.234"]})]
          not-expected-messages [(json/generate-string {:oid ["not-expected-321.321.321"] :noid ["not-expected-321.321"]})
                                 (json/generate-string {:oid ["not-expected-432.432.432"] :boid ["not-expected-423.423"]})]]

      (testing-with-queues-fixture "Queue should contain SQS queues"
                                   (is (str/ends-with? (queue :priority) "/koutaIndeksoijaPriority"))
                                   (is (str/ends-with? (queue :fast) "/koutaIndeksoijaFast"))
                                   (is (str/ends-with? (queue :slow) "/koutaIndeksoijaSlow"))
                                   (is (str/ends-with? (queue :dlq) "/koutaIndeksoijaDlq"))
                                   (is (str/ends-with? (queue :notifications) "/koutaIndeksoijaNotifications"))
                                   (is (str/ends-with? (queue :notifications-dlq) "/koutaIndeksoijaNotificationsDlq")))

      (testing "Receive-messages-from-queues should"
        (testing-with-queues-fixture "receive messages from :priority queue first"
                                     (doseq [msg expected-messages] (send-message (queue :priority) msg))
                                     (doseq [msg not-expected-messages] (send-message (queue :fast) msg))
                                     (doseq [msg not-expected-messages] (send-message (queue :slow) msg))
                                     (let [received-message-bodies (message-bodies (receive-messages-from-queues))]
                                       (is (contains-same-elements-in-any-order? expected-messages received-message-bodies))))

        (testing-with-queues-fixture "receive message from :priority even if they appear after started polling (use long poll)"
                                     (doseq [msg not-expected-messages] (send-message (queue :fast) msg))
                                     (doseq [msg not-expected-messages] (send-message (queue :slow) msg))
                                     (let [receive (future (receive-messages-from-queues))]
                                       ;Jos tässä lähettää monta viestiä esim. doseq:lla, futureen tulee aina vain niistä ensimmäinen,
                                       ;koska se tulkitsee operaation valmistuneen ensimmäisen viestin jälkeen.
                                       (send-message (queue :priority) (first expected-messages))
                                       (is (contains-same-elements-in-any-order? [(first expected-messages)] (message-bodies @receive)))))

        (testing-with-queues-fixture "receive messages from fast queue if there are no messages in :priority queue"
                                     (doseq [msg expected-messages] (send-message (queue :fast) msg))
                                     (doseq [msg not-expected-messages] (send-message (queue :slow) msg))
                                     (is (contains-same-elements-in-any-order? expected-messages (message-bodies (receive-messages-from-queues)))))

        (testing-with-queues-fixture "receive messages from slow queue only if there are no messages in other queues"
                                     (doseq [msg expected-messages] (send-message (queue :slow) msg))
                                     (is (contains-same-elements-in-any-order? expected-messages (message-bodies (receive-messages-from-queues)))))

        (testing-with-queues-fixture "long poll if there are no messages in any queue"
                                     (let [start (System/currentTimeMillis)]
                                       (message-bodies (receive-messages-from-queues))
                                       (is (< (- (* 1000 test-long-poll-time) 500)
                                              (- (System/currentTimeMillis) start)
                                              (+ (* 1000 test-long-poll-time) 500))))))

      (testing "Handle-messages-from-queues should"
        (testing-with-queues-fixture "receive parsed messages from queues and call 'handler' function for them"
                                     (doseq [msg expected-messages] (send-message (queue :priority) msg))
                                     (is (contains-same-elements-in-any-order? (map #(json/parse-string % true) expected-messages)
                                                                               (let [handled (atom ())]
                                                                                 (handle-messages-from-queues (fn [received] (swap! handled concat received)))
                                                                                 @handled))))

        (testing-with-queues-fixture "delete messages after successful handling"
                                     (doseq [msg expected-messages] (send-message (queue :priority) msg))
                                     (let [priority-queue (queue :priority)
                                           deleted        (atom [])]
                                       (with-redefs [delete-message (fn [& {:keys [queue-url]}] (swap! deleted conj queue-url))]
                                         (handle-messages-from-queues (constantly ()))
                                         (is (= (count expected-messages) (count (filter #(= priority-queue %) @deleted)))))))

        (testing-with-queues-fixture "not delete messages before successful handling"
                                     (doseq [msg expected-messages] (send-message (queue :priority) msg))
                                     (let [deleted (atom [])]
                                       (with-redefs [delete-message (fn [& {:keys [queue-url]}] (swap! deleted conj queue-url))]
                                         (try
                                           (handle-messages-from-queues (fn [_] (throw (new Exception))))
                                           (catch Exception e)))
                                       (is (= [] @deleted)))))

      (testing "Notification-queue handle-messages-from-queues should"

        (testing-with-queues-fixture "receive parsed messages from queues and call 'handler' function for them"
                                     (doseq [msg expected-messages] (send-message (queue :notifications) msg))
                                     ; TODO: Miksi notifications-jonolle käytetään erilaista handle-messages-from-queues funktiota kuin muille jonoille?
                                     (is (contains-same-elements-in-any-order? (mapcat #(into [] (json/parse-string % true)) expected-messages)
                                                                               (let [handled (atom ())]
                                                                                 (notification-queue/handle-messages-from-queues (fn [received] (swap! handled concat received)))
                                                                                 @handled))))

        (testing-with-queues-fixture "delete messages after successful handling"
                                     (doseq [msg expected-messages] (send-message (queue :notifications) msg))
                                     (let [notifications (queue :notifications)
                                           deleted        (atom [])]
                                       (with-redefs [delete-message (fn [& {:keys [queue-url]}] (swap! deleted conj queue-url))]
                                         (notification-queue/handle-messages-from-queues (constantly ()))
                                         (is (= (count expected-messages) (count (filter #(= notifications %) @deleted)))))))

        (testing-with-queues-fixture "not delete messages before successful handling"
                                     (doseq [msg expected-messages] (send-message (queue :notifications) msg))
                                     (let [deleted (atom [])]
                                       (with-redefs [delete-message (fn [& {:keys [queue-url]}] (swap! deleted conj queue-url))]
                                         (try
                                           (notification-queue/handle-messages-from-queues (fn [_] (throw (new Exception))))
                                           (catch Exception e)))
                                       (is (= [] @deleted))))))))
