(ns kouta-indeksoija-service.indexer.kouta-haku-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]))

(use-fixtures :each common-indexer-fixture)

(deftest index-hakukohteet-without-hakukohdekoodiuri-to-haku-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohteet with original name to haku-index, if hakukohdeKoodiUri not available"
      (check-all-nil)
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (let [hakukohteet (to-array (:hakukohteet (get-doc haku/index-name haku-oid)))]
        (is (= 2 (alength hakukohteet)))
        (is (= {:fi "Koulutuksen 0 toteutuksen 0 hakukohde 0 fi",
                :sv "Koulutuksen 0 toteutuksen 0 hakukohde 0 sv"} (:nimi (aget hakukohteet 0))))
        (is (= {:fi "Koulutuksen 0 toteutuksen 2 hakukohde 0 fi",
                :sv "Koulutuksen 0 toteutuksen 2 hakukohde 0 sv"} (:nimi (aget hakukohteet 1))))))))

(deftest quick-index-haku-test
  (fixture/with-mocked-indexing
    (testing "Indexer should quick-index only haku to haku index"
      (check-all-nil)
      (i/quick-index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= (:oid (get-doc haku/index-name haku-oid)) haku-oid)))))

(deftest index-hakukohteet-with-hakukohdekoodiuri-to-haku-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohteet with hakukohdeKoodiUri to haku-index, if hakukohdeKoodiUri available"
      (check-all-nil)
      (fixture/update-hakukohde-mock hakukohde-oid :hakukohdeKoodiUri "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1" :nimi {})
      (fixture/update-hakukohde-mock hakukohde-oid2 :hakukohdeKoodiUri "hakukohteetperusopetuksenjalkeinenyhteishaku_101#2" :nimi {})
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (let [hakukohteet (to-array (:hakukohteet (get-doc haku/index-name haku-oid)))]
        (is (= 2 (alength hakukohteet)))
        (is (= {:fi "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi fi",
                :sv "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi sv"} (:nimi (aget hakukohteet 0))))
        (is (= {:fi "hakukohteetperusopetuksenjalkeinenyhteishaku_101#2 nimi fi",
                :sv "hakukohteetperusopetuksenjalkeinenyhteishaku_101#2 nimi sv"} (:nimi (aget hakukohteet 1))))))))

(deftest index-julkaistut-hakukohteet-of-ei-julkaistu-haku-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde in same state as related haku, in case hakukohde was published and haku not"
      (check-all-nil)
      (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (let [hakukohteet (to-array (:hakukohteet (get-doc haku/index-name ei-julkaistu-haku-oid)))]
        (is (= 1 (alength hakukohteet)))
        (is (= "tallennettu" (:tila (aget hakukohteet 0))))))))

(deftest delete-non-existing-haku
  (fixture/with-mocked-indexing
    (testing "Indexer should delete non-existing haku from haku-index"
      (check-all-nil)
      (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "tallennettu")
      (i/index-haut [haku-oid ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc haku/index-name haku-oid))))
      (is (= "tallennettu" (:tila (get-doc haku/index-name ei-julkaistu-haku-oid))))
      (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "poistettu")
      (fixture/update-haku-mock ei-julkaistu-haku-oid :tila "poistettu")
      (i/index-haut [haku-oid ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc haku/index-name haku-oid))))
      (is (nil? (get-doc haku/index-name ei-julkaistu-haku-oid))))))

(deftest delete-non-existing-haku-from-search-index
  (fixture/with-mocked-indexing
    (testing "Indexer should delete non-existing haku from search index"
      (check-all-nil)
      (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "tallennettu")
      (fixture/update-hakukohde-mock hakukohde-oid2 :tila "arkistoitu")
      (fixture/update-toteutus-mock toteutus-oid2 :tila "arkistoitu")
      (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (is (= "tallennettu" (:tila (get-doc haku/index-name ei-julkaistu-haku-oid))))
      (is (= "tallennettu" (:tila (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid))))
      (is (= toteutus-oid3 (:oid (get-doc toteutus/index-name toteutus-oid3))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= false (search-terms-key-not-empty oppilaitos-search/index-name oppilaitos-oid :hakutiedot)))
      (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "poistettu")
      (fixture/update-haku-mock ei-julkaistu-haku-oid :tila "poistettu")
      (fixture/update-toteutus-mock toteutus-oid3 :tila "poistettu")
      (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu")
      (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc haku/index-name ei-julkaistu-haku-oid)))
      (is (nil? (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid)))
      (is (nil? (get-doc toteutus/index-name toteutus-oid3)))
      (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
      (is (= false (search-terms-key-not-empty oppilaitos-search/index-name oppilaitos-oid :hakutiedot))))))

(deftest delete-nil-haku
  (fixture/with-mocked-indexing
    (testing "Indexer should delete haku that does not exist in kouta and related hakukohde"
      (check-all-nil)
      (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (fixture/update-haku-mock ei-julkaistu-haku-oid) ;;Päivitetään haun arvoksi nil
      (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc haku/index-name ei-julkaistu-haku-oid)))
      (is (nil? (get-doc haku/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid))))))

(deftest maksullinen-kk-haku
  (fixture/with-mocked-indexing
    (testing "Indexer should index haku as maksullinen kk-haku in case conditions fulfilled in haku and toteutukset"
      (check-all-nil)
      (fixture/update-haku-mock haku-oid :hakuajat [{:alkaa "2024-03-15T09:58" :paattyy "2024-04-15T09:58"}])
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= false (:maksullinenKkHaku (get-doc haku/index-name haku-oid))))
      (fixture/update-haku-mock haku-oid :metadata fixture/maksullinen-kk-haku-metadata)
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= false (:maksullinenKkHaku (get-doc haku/index-name haku-oid))))
      (fixture/update-haku-mock haku-oid :hakuajat [{:alkaa "2025-03-15T09:58" :paattyy "2025-04-15T09:58"}])
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= false (:maksullinenKkHaku (get-doc haku/index-name haku-oid))))
      (fixture/update-haku-mock haku-oid :kohdejoukkoKoodiUri "haunkohdejoukko_12#1")
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= false (:maksullinenKkHaku (get-doc haku/index-name haku-oid))))
      (fixture/update-toteutus-mock toteutus-oid :johtaaTutkintoon true)
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (is (= true (:maksullinenKkHaku (get-doc haku/index-name haku-oid)))))))

