(ns kouta-indeksoija-service.indexer.common-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.indexer.kouta.common :refer [koodi-uri?]]
            [kouta-indeksoija-service.test-tools :refer [compare-json]]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.indexer.kouta.common :as common]))

(deftest is-koodi-uri-test
  (testing "Check what is interpreted as koodiUri and what is not"
    (is (some? (koodi-uri? "tutkintonimikekk_411#2")))
    (is (some? (koodi-uri? "tutkintonimikekk_221-1#233")))
    (is (some? (koodi-uri? "koulutus_371101#1")))
    (is (some? (koodi-uri? "koulutus_371101")))
    (is (empty? (koodi-uri? "tutkintonim?ikekk_221-1#234")))))

(deftest clean-non-kielivalinta-langs-test
  (testing "Check that langs not in kielivalinta is cleaned from form"
    (compare-json (no-timestamp (json "kouta-hakukohde-result-only-fi"))
                  (no-timestamp (common/clean-langs-not-in-kielivalinta (json "kouta-hakukohde-result-kielivalinta-fi"))))))

(deftest is_postinumerokoodiuri?-test
  (testing "returns true for postinumerokoodiuri without version"
    (is (= true (common/is-postinumerokoodiuri? "posti_90500"))))

  (testing "returns true for postinumerokoodiuri with version"
    (is (= true (common/is-postinumerokoodiuri? "posti_90500#2"))))

  (testing "returns false for non-postinumerokoodiuri string"
    (is (= false (common/is-postinumerokoodiuri? "tutkintonimikekk_411#2"))))
)
