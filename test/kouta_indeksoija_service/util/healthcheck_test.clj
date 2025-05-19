(ns kouta-indeksoija-service.util.healthcheck-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [kouta-indeksoija-service.api :refer :all]
            [kouta-indeksoija-service.test-tools :refer [debug-pretty]]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :refer [->keywordized-json]])
  (:import (software.amazon.awssdk.services.sqs.model QueueAttributeName)))

(intern 'clj-log.access-log 'service "kouta-indeksoija")

(defn mock-queue-attributes
  [nr p & a]
  {(QueueAttributeName/APPROXIMATE_NUMBER_OF_MESSAGES) (str nr)
   (QueueAttributeName/QUEUE_ARN) (str "koutaQueue" p)})

(defn mock-cluster-health
  [c-health i-health]
  {:cluster_health {:cluster_name "moi" :status c-health}
   :indices-info [{:index "kiva-indeksi" :health i-health}]})

(defn body->json
  [r]
  (->keywordized-json (slurp (:body r))))

(defn expected-sqs-health
  [nr healthy]
  {:priority {:QueueArn "koutaQueue:priority", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10},
   :fast {:QueueArn "koutaQueue:fast", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10},
   :slow {:QueueArn "koutaQueue:slow", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10},
   :dlq {:QueueArn "koutaQueue:dlq", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10},
   :notifications {:QueueArn "koutaQueue:notifications", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10},
   :notifications-dlq {:QueueArn "koutaQueue:notifications-dlq", :ApproximateNumberOfMessages (str nr), :healthy healthy, :health-threshold 10}})

(defn expected-queue-health
  [c-status c-healthy i-status i-healthy]
  {:cluster_health {:cluster "moi", :status c-status, :healthy c-healthy},
   :indices_health [{:index "kiva-indeksi", :status i-status, :healthy i-healthy}]})

(deftest healthcheck-test
  (testing "healthcheck returns 200"
    (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck"))]
      (is (= 200 (:status response)))))

  (testing "deep healthcheck returns 200"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (partial mock-queue-attributes 5)
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (partial mock-cluster-health "yellow" "yellow")]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 200 (:status response)))
        (is (= {:sqs-health (expected-sqs-health 5 true),
                :elasticsearch-health (expected-queue-health "yellow" true "yellow" true)} (body->json response))))))

  (testing "deep healthcheck returns 500 if queue is too full"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (partial mock-queue-attributes 12)
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (partial mock-cluster-health "green" "green")]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 500 (:status response)))
        (is (= {:sqs-health (expected-sqs-health 12 false),
                :elasticsearch-health (expected-queue-health "green" true "green" true)} (body->json response))))))

  (testing "deep healthcheck returns 500 if cluster is not healthy"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (partial mock-queue-attributes 5)
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (partial mock-cluster-health "red" "green")]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 500 (:status response)))
        (is (= {:sqs-health (expected-sqs-health 5 true),
                :elasticsearch-health (expected-queue-health "red" false "green" true)} (body->json response))))))

  (testing "deep healthcheck returns 500 if index is not healthy"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (partial mock-queue-attributes 5)
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (partial mock-cluster-health "green" "red")]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 500 (:status response)))
        (is (= {:sqs-health (expected-sqs-health 5 true),
                :elasticsearch-health (expected-queue-health "green" true "red" false)} (body->json response))))))

  (testing "deep healthcheck returns 500 queue throws exception"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (fn [p & a] (throw (Exception. "Tämä on tarkoituksellinen testin poikkeus")))
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (partial mock-cluster-health "yellow" "yellow")
                  clojure.tools.logging/log* (fn [logger level throwable message])]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 500 (:status response)))
        (is (= {:sqs-health {:error "Tämä on tarkoituksellinen testin poikkeus"},
                :elasticsearch-health (expected-queue-health "yellow" true "yellow" true)} (body->json response))))))

  (testing "deep healthcheck returns 500 elasticsearch throws exception"
    (with-redefs [kouta-indeksoija-service.queue.sqs/get-queue-attributes (partial mock-queue-attributes 5)
                  kouta-indeksoija-service.elastic.admin/get-elastic-status (fn [] (throw (Exception. "Tämän on tarkoituksellinen testin poikkeus")))
                  clojure.tools.logging/log* (fn [logger level throwable message])]
      (let [response (app (mock/request :get "/kouta-indeksoija/api/healthcheck/deep"))]
        (is (= 500 (:status response)))
        (is (= {:sqs-health (expected-sqs-health 5 true),
                :elasticsearch-health {:error "Tämän on tarkoituksellinen testin poikkeus"}} (body->json response)))))))

