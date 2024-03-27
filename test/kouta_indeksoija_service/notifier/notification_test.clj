(ns kouta-indeksoija-service.notifier.notification-test
  (:require [clj-test-utils.elasticsearch-mock-utils :refer :all]
            [clojure.test :refer :all]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.test-tools :refer [parse compare-json]]
            [mocks.notifier-target-mock :as notifier-target-mock]
            [kouta-indeksoija-service.queue.queue :as queue]))

(use-fixtures :once fixture/reload-kouta-indexer-fixture)
(use-fixtures :each notifier-target-mock/notifier-mock-fixture)

(use-fixtures :once (fn [tests] (admin/initialize-indices) (tests)) common-indexer-fixture)

(defmacro with-mocked-notifications
  [& body]
  ;TODO: with-redefs is not thread safe and may cause unexpected behaviour.
  ;It can be temporarily fixed by using locked in mocking functions, but better solution would be superb!
  `(with-redefs [kouta-indeksoija-service.notifier.notifier/queue-notification-messages
                 mocks.notifier-target-mock/add]
     (do ~@body)))


(deftest koulutus-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about the koulutus should be sent when handling messages from queue"
      (queue/handle-messages [{:koulutukset [koulutus-oid]}])
      (compare-json (json "kouta-koulutus-notification")
                    (get @notifier-target-mock/received koulutus-oid))))))

(deftest toteutus-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about the toteutus should be sent when handling messages from queue"
      (queue/handle-messages [{:toteutukset [toteutus-oid]}])
      (compare-json (json "kouta-toteutus-notification")
                    (get @notifier-target-mock/received toteutus-oid))))))

(deftest haku-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about the haku should be sent after indexing"
      (queue/handle-messages [{:haut [haku-oid]}])
      (compare-json (json "kouta-haku-notification")
                    (get @notifier-target-mock/received haku-oid))))))

(deftest hakukohde-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about the hakukohde should be sent after indexing"
      (queue/handle-messages [{:hakukohteet [hakukohde-oid]}])
      (compare-json (json "kouta-hakukohde-notification")
                    (get @notifier-target-mock/received hakukohde-oid))))))

(deftest valintaperuste-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about the valintaperuste should be sent after indexing"
      (queue/handle-messages [{:valintaperusteet [valintaperuste-id]}])
      (compare-json (json "kouta-valintaperuste-notification")
                    (get @notifier-target-mock/received valintaperuste-id))))))

(deftest oids-notification-test
  (with-mocked-notifications
   (fixture/with-mocked-indexing
    (testing "Notifications about all relevant objects should be sent if enabled"
      (queue/handle-messages [{:koulutukset [koulutus-oid]
                               :haut [haku-oid]
                               :hakukohteet [hakukohde-oid]
                               :toteutukset [toteutus-oid]
                               :valintaperusteet [valintaperuste-id]
                               :sorakuvaukset [sorakuvaus-id]
                               :oppilaitokset [oppilaitos-oid]}])
      (are [id] (contains? @notifier-target-mock/received id)
                koulutus-oid
                haku-oid
                hakukohde-oid
                toteutus-oid
                valintaperuste-id
                sorakuvaus-id)
      (are [id] (empty? (get @notifier-target-mock/received id))
                oppilaitos-oid)))))
