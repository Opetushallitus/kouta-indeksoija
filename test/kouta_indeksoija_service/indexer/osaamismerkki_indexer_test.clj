(ns kouta-indeksoija-service.indexer.osaamismerkki-indexer-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.indexer.eperuste.osaamismerkki :as osaamismerkki]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]))

(use-fixtures :once (fn [tests]
                      (admin/initialize-eperuste-indices-for-reindexing)
                      (tests)))

(deftest osaamismerkki-index-test
  (fixture/with-mocked-indexing
    (testing "do index osaamismerkki"
      (i/index-osaamismerkit ["osaamismerkit_1008"] (. System (currentTimeMillis)))
      (let [indexed-osaamismerkki (osaamismerkki/get-from-index "osaamismerkit_1008")]
        (is (= "osaamismerkit_1008" (get indexed-osaamismerkki :koodiUri)))))))
