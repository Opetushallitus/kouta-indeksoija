(ns kouta-indeksoija-service.scheduled.changed-data-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.scheduled.jobs :as jobs]
            [kouta-indeksoija-service.test-tools :as tools]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.elastic.admin :refer :all]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]))

(def test-queue (atom []))
(defonce last-modified (System/currentTimeMillis))
(def muuttunut-oppilaitos-oid1 "1.2.246.562.10.54453921329")
(def muuttunut-oppilaitos-oid2 "1.2.246.562.10.197113642410")

(defn mock-queue-name
  [q]
  (if (= :fast q) "fast-queue" nil))

(defn mock-send-message
  [q m]
  (is (= "fast-queue" q))
  (swap! test-queue conj m))

(defn mock-organisaatio-changes [_]
  (tools/parse (str "test/resources/organisaatiot/last-modified.json")))

(defn mock-eperuste-changes
  [_]
  ["111" "222" "333"])

(defn mock-used-eperusteet
  []
  ["222" "333" "444" "555"])

(deftest changed-data-test
  (kouta-indeksoija-service.elastic.admin/initialize-indices)

  (fixture/add-oppilaitos-mock
   (let [organisaatio-with-children (fixture/->keywordized-json (slurp "test/resources/organisaatiot/1.2.246.562.10.54453921329-kouta.json"))]
     (-> {:oid muuttunut-oppilaitos-oid1}
         (assoc-in [:_enrichedData :organisaatio] organisaatio-with-children))))

  (fixture/add-oppilaitos-mock
   (let [organisaatio-with-children (fixture/->keywordized-json (slurp "test/resources/organisaatiot/1.2.246.562.10.197113642410-kouta.json"))]
     (-> {:oid muuttunut-oppilaitos-oid2}
         (assoc-in [:_enrichedData :organisaatio] organisaatio-with-children))))

  (fixture/with-mocked-indexing
    (with-redefs [kouta-indeksoija-service.queue.sqs/queue mock-queue-name
                  kouta-indeksoija-service.queue.sqs/send-message mock-send-message
                  kouta-indeksoija-service.queuer.last-queued/get-last-queued-time (fn [] last-modified)
                  kouta-indeksoija-service.queuer.last-queued/set-last-queued-time (fn [t] (is (< last-modified t)))]
      (testing "Changed data update should"
        (testing "handle changes correctly when both eperuste- and organisation -changes"
          (with-redefs [kouta-indeksoija-service.rest.organisaatio/find-last-changes mock-organisaatio-changes
                        kouta-indeksoija-service.rest.eperuste/find-changes mock-eperuste-changes
                        kouta-indeksoija-service.rest.kouta/list-used-eperuste-ids-with-cache mock-used-eperusteet]
            (fixture/delete-all-elastic-data)
            (reset! test-queue [])
            (jobs/handle-and-queue-changed-data)
            (is (= muuttunut-oppilaitos-oid1
                   (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid1))))
            (is (= muuttunut-oppilaitos-oid2 (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid2))))
            (is (nil? (:oid (get-doc oppilaitos-search/index-name muuttunut-oppilaitos-oid1)))) ;; ei löydy koulutuksia
            (is (nil? (:oid (get-doc oppilaitos-search/index-name muuttunut-oppilaitos-oid2)))) ;; ei löydy koulutuksia
            (is (tools/contains-same-elements-in-any-order? ["111" "222" "333" "444" "555"] (-> @test-queue (first) :eperusteet)))))

        (testing "handle changes correctly when organisation-changes only"
          (with-redefs [kouta-indeksoija-service.rest.organisaatio/find-last-changes mock-organisaatio-changes
                        kouta-indeksoija-service.rest.eperuste/find-changes (fn [l] [])
                        kouta-indeksoija-service.rest.kouta/list-used-eperuste-ids-with-cache (fn [] [])]
            (fixture/delete-all-elastic-data)
            (reset! test-queue [])
            (jobs/handle-and-queue-changed-data)
            (is (= muuttunut-oppilaitos-oid1 (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid1))))
            (is (= muuttunut-oppilaitos-oid2 (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid2))))
            (is (= [] @test-queue))))

        (testing "handle changes correctly when eperuste-changes only"
          (with-redefs [kouta-indeksoija-service.rest.organisaatio/find-last-changes (fn [l] [])
                        kouta-indeksoija-service.rest.eperuste/find-changes mock-eperuste-changes
                        kouta-indeksoija-service.rest.kouta/list-used-eperuste-ids-with-cache mock-used-eperusteet]
            (fixture/delete-all-elastic-data)
            (reset! test-queue [])
            (jobs/handle-and-queue-changed-data)
            (is (nil? (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid1))))
            (is (nil? (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid2))))
            (is (tools/contains-same-elements-in-any-order? ["111" "222" "333" "444" "555"] (-> @test-queue (first) :eperusteet)))))

        (testing "cope with situation when no changes"
          (with-redefs [kouta-indeksoija-service.rest.organisaatio/find-last-changes (fn [l] [])
                        kouta-indeksoija-service.rest.eperuste/find-changes (fn [l] [])
                        kouta-indeksoija-service.rest.kouta/list-used-eperuste-ids-with-cache (fn [] [])]
            (fixture/delete-all-elastic-data)
            (reset! test-queue [])
            (jobs/handle-and-queue-changed-data)
            (is (nil? (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid1))))
            (is (nil? (:oid (get-doc oppilaitos/index-name muuttunut-oppilaitos-oid2))))
            (is (= [] @test-queue))))))))

