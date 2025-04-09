(ns kouta-indeksoija-service.queuer.queuer-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.queuer.queuer :as q]
            [kouta-indeksoija-service.test-tools :refer [contains-same-elements-in-any-order? parse]]))

(def test-queue (atom []))
(defonce last-modified (System/currentTimeMillis))

(defn mock-queue-name
  [q]
  (if (= :fast q) "fast-queue" nil))

(defn mock-send-message
  [q m]
  (is (= "fast-queue" q))
  (swap! test-queue conj m))

(defn mock-eperuste-oids
  []
  (vec (map #(str %) (range 21))))

(defn mock-organisaatio-hierarkia
  []
  (atom {"1.2.246.562.10.1" {:oid "1.2.246.562.10.1" :organisaatiotyypit ["organisaatiotyyppi_02"] :status "AKTIIVINEN"},
         "1.2.246.562.10.2" {:oid "1.2.246.562.10.2" :organisaatiotyypit ["organisaatiotyyppi_03"] :status "AKTIIVINEN"},
         "1.2.246.562.10.3" {:oid "1.2.246.562.10.3" :organisaatiotyypit ["organisaatiotyyppi_02"] :status "PASSIIVINEN"},
         "1.2.246.562.10.4" {:oid "1.2.246.562.10.4" :organisaatiotyypit ["organisaatiotyyppi_02"] :status "AKTIIVINEN"}}))

(defn mock-eperuste-changes
  [l]
  ["111" "222" "333"])

(defn mock-used-eperusteet
  []
  ["222" "333" "444" "555"])

(deftest queuer-test
  (testing "Queuer should"
    (with-redefs [kouta-indeksoija-service.queue.sqs/queue mock-queue-name
                  kouta-indeksoija-service.queue.sqs/send-message mock-send-message
                  kouta-indeksoija-service.rest.eperuste/find-all mock-eperuste-oids
                  kouta-indeksoija-service.indexer.cache.hierarkia/get-hierarkia-cached mock-organisaatio-hierarkia
                  kouta-indeksoija-service.rest.eperuste/find-changes mock-eperuste-changes
                  kouta-indeksoija-service.queuer.last-queued/get-last-queued-time (fn [] last-modified)
                  kouta-indeksoija-service.queuer.last-queued/set-last-queued-time (fn [t] (is (< last-modified t)))
                  kouta-indeksoija-service.rest.kouta/list-used-eperuste-ids-with-cache mock-used-eperusteet]

      (testing "queue eperuste"
        (q/queue-eperuste "123")
        (is (= [{:eperusteet ["123"]}] @test-queue))
        (reset! test-queue []))

      (testing "queue all eperusteet"
        (q/queue-all-eperusteet)
        (is (= [{:eperusteet (vec (map str (range 20)))} {:eperusteet ["20"]}] @test-queue))
        (reset! test-queue []))

      (testing "queue organisaatio"
        (q/queue-oppilaitos "123")
        (is (= [{:oppilaitokset ["123"]}] @test-queue))
        (reset! test-queue []))

      (testing "queue all indexable oppilaitokset"
        (q/queue-all-oppilaitokset-from-organisaatiopalvelu)
        (is (= [{:oppilaitokset ["1.2.246.562.10.1" "1.2.246.562.10.2" "1.2.246.562.10.4"]}] @test-queue))
        (reset! test-queue []))

      (testing "queue changes"
        (q/queue-used-or-changed-eperusteet (System/currentTimeMillis))
        (is (= 1 (count @test-queue)))
        (is (contains-same-elements-in-any-order? ["111" "222" "333" "444" "555"] (-> @test-queue (first) :eperusteet)))
        (reset! test-queue [])))))
