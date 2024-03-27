(ns kouta-indeksoija-service.indexer.kouta-indexer-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.valintaperuste :as valintaperuste]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.sorakuvaus :as sorakuvaus]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :as eperuste]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.test-tools :refer [compare-json debug-pretty]]
            [kouta-indeksoija-service.indexer.cache.hierarkia]
            [kouta-indeksoija-service.rest.organisaatio]
            [kouta-indeksoija-service.rest.kouta]
            [clj-test-utils.elasticsearch-mock-utils :refer :all]))

(use-fixtures :once fixture/reload-kouta-indexer-fixture)
(use-fixtures :each common-indexer-fixture)

(def oppilaitos-with-wrong-type "1.2.246.562.10.14452275770")

(deftest index-haku-test-1
  (fixture/with-mocked-indexing
    (testing "Indexer should index haku to haku index and update related indexes"
      (check-all-nil)
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (compare-json (no-timestamp (json "kouta-haku-result"))
                    (no-timestamp (get-doc haku/index-name haku-oid)))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (nil? (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid)))))))

(deftest index-haku-hakulomakelinkki-test
  (fixture/with-mocked-indexing
    (testing "Indexer should create hakulomakeLinkki from haku oid"
      (check-all-nil)
      (fixture/update-haku-mock haku-oid :hakulomaketyyppi "ataru")
      (i/index-haut [haku-oid] (. System (currentTimeMillis)))
      (compare-json {:fi (str "http://localhost/hakemus/haku/" haku-oid "?lang=fi")
                     :sv (str "http://localhost/hakemus/haku/" haku-oid "?lang=sv")
                     :en (str "http://localhost/hakemus/haku/" haku-oid "?lang=en")}
                    (:hakulomakeLinkki (get-doc haku/index-name haku-oid))))))

(deftest index-valintaperuste-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index valintaperuste to valintaperuste index and related hakukohde to hakukohde index"
      (check-all-nil)
      (i/index-valintaperusteet [valintaperuste-id] (. System (currentTimeMillis)))
      (compare-json (no-timestamp (json "kouta-valintaperuste-result"))
                    (no-timestamp (get-doc valintaperuste/index-name valintaperuste-id)))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= hakukohde-oid2 (:oid (get-doc hakukohde/index-name hakukohde-oid2))))
      (is (= ei-julkaistun-haun-julkaistu-hakukohde-oid (:oid (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid))))
      (fixture/update-hakukohde-mock hakukohde-oid :tila "poistettu")
      (fixture/update-hakukohde-mock hakukohde-oid2 :tila "poistettu")
      (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "poistettu")
      (fixture/update-valintaperuste-mock valintaperuste-id :tila "poistettu")
      (i/index-valintaperusteet [valintaperuste-id] (. System (currentTimeMillis)))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id)))
      (is (nil? (get-doc hakukohde/index-name hakukohde-oid)))
      (is (nil? (get-doc hakukohde/index-name hakukohde-oid2)))
      (is (nil? (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid))))))

(deftest index-sorakuvaus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index sorakuvaus to sorakuvaus index and koulutus related to sorakuvaus to koulutus index"
      (check-all-nil)
      (i/index-sorakuvaukset [sorakuvaus-id] (. System (currentTimeMillis)))
      (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid2 (:oid (get-doc koulutus/index-name koulutus-oid2))))
      (compare-json (no-timestamp (json "kouta-sorakuvaus-result"))
                    (no-timestamp (get-doc sorakuvaus/index-name sorakuvaus-id)))
      (fixture/update-sorakuvaus-mock sorakuvaus-id :tila "poistettu")
      (fixture/update-koulutus-mock koulutus-oid :tila "poistettu")
      (fixture/update-koulutus-mock koulutus-oid2 :tila "poistettu")
      (i/index-sorakuvaukset [sorakuvaus-id] (. System (currentTimeMillis)))
      (is (nil? (get-doc sorakuvaus/index-name sorakuvaus-id)))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (nil? (get-doc koulutus/index-name koulutus-oid2))))))

(defn- add-toteutus-for-oppilaitos []
  (fixture/add-koulutus-mock "1.2.246.562.13.00000000000000000002"
                             :tila "julkaistu"
                             :nimi "Autoalan perustutkinto 1"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :julkinen "true"
                             :modified "2019-01-31T09:11:23"
                             :tarjoajat oppilaitos-oid2)
  (fixture/add-toteutus-mock "1.2.246.562.17.00000000000000000001"
                             "1.2.246.562.13.00000000000000000002"
                             :tila "julkaistu"
                             :nimi "Autoalan perustutkinto 1"
                             :tarjoajat oppilaitos-oid2))

(deftest index-oppilaitos-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index oppilaitos and it's osat to oppilaitos index"
      (check-all-nil)
      (add-toteutus-for-oppilaitos)
      (i/index-oppilaitokset [oppilaitos-oid2] (. System (currentTimeMillis)))
      (compare-json (no-timestamp (json "kouta-oppilaitos-result"))
                    (no-timestamp (get-doc oppilaitos/index-name oppilaitos-oid2))))))

(deftest index-oppilaitos-test-2
  (fixture/with-mocked-indexing
    (testing "Indexer should index oppilaitos and it's osat to oppilaitos index when given oppilaitoksen osa oid"
      (check-all-nil)
      (add-toteutus-for-oppilaitos)
      (i/index-oppilaitos oppilaitoksen2-osa-oid)
      (compare-json (no-timestamp (json "kouta-oppilaitos-result"))
                    (no-timestamp (get-doc oppilaitos/index-name oppilaitos-oid2))))))

(deftest index-oppilaitos-test-3
  (fixture/with-mocked-indexing
    (testing "Indexer should not index oppilaitos when invalid organisaatiotyyppi"
      (check-all-nil)
      (i/index-oppilaitos oppilaitos-with-wrong-type)
      (check-all-nil))))

(deftest index-oppilaitos-test-4
  (fixture/with-mocked-indexing
    (testing "Indexer should index also koulutus when indexing oppilaitos"
      (check-all-nil)
      (i/index-oppilaitokset [oppilaitos-oid] (. System (currentTimeMillis)))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid)))))))

(deftest index-oppilaitoksen-osa
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde when indexing oppilaitoksen osa that is set as järjestyspaikka on that hakukohde"
      (check-all-nil)
      (i/index-oppilaitokset [default-jarjestyspaikka-oid] (. System (currentTimeMillis)))
      (is (= "1.2.246.562.20.00000000000000000002" (:oid (get-doc hakukohde/index-name "1.2.246.562.20.00000000000000000002")))))))

(deftest index-oppilaitoksen-osa-2
  (fixture/with-mocked-indexing
    (testing "Indexer should index toteutus when indexing oppilaitoksen osa"
      (check-all-nil)
      (i/index-oppilaitokset [oppilaitoksen-osa-oid] (. System (currentTimeMillis)))
      (is (= toteutus-oid2 (:oid (get-doc toteutus/index-name toteutus-oid2)))))))

(deftest index-organisaatio-no-oppilaitokset-test
  (fixture/with-mocked-indexing
    (with-redefs [kouta-indeksoija-service.rest.kouta/get-koulutukset-by-tarjoaja (fn [oid] (throw (Exception. (str "I was called with [" oid "]"))))]
      (testing "Indexer should not index organisaatio without oppilaitos"
        (check-all-nil)
        (i/index-oppilaitos "1.2.246.562.10.101010101012222222")
        (check-all-nil)))))

(deftest index-all-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index all"
      (let [eperuste-id 12321]
        (fixture/update-koulutus-mock
         koulutus-oid
         :ePerusteId eperuste-id
         :tarjoajat ["1.2.246.562.10.54545454545"])
        (check-all-nil)
        (is (nil? (eperuste/get-from-index eperuste-id)))
        (i/index-all-kouta)
        (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
        (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
        (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
        (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid))))
        (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
        (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
        (is (= valintaperuste-id (:id (get-doc valintaperuste/index-name valintaperuste-id))))
        (is (= eperuste-id (:id (eperuste/get-from-index eperuste-id))))
        (fixture/update-koulutus-mock koulutus-oid :ePerusteId nil)))))

(deftest index-changes-oids-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index changed oids"
      (check-all-nil)
      (i/index-oids {:hakukohteet [hakukohde-oid]} (. System (currentTimeMillis)))
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (nil? (:oid (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (nil? (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (nil? (:id (get-doc valintaperuste/index-name valintaperuste-id)))))))

(deftest index-changes-oids-test-2
  (fixture/with-mocked-indexing
    (testing "Indexer should index changed oids 2"
      (check-all-nil)
      (i/index-oids {:sorakuvaukset [sorakuvaus-id]} (. System (currentTimeMillis)))
      (is (nil? (:oid (get-doc haku/index-name haku-oid))))
      (is (nil? (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (nil? (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid))))
      (is (nil? (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (nil? (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (nil? (:id (get-doc valintaperuste/index-name valintaperuste-id)))))))

(deftest index-all-koulutukset-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index all koulutukset"
      (check-all-nil)
      (i/index-all-koulutukset)
      (is (nil? (get-doc haku/index-name haku-oid)))
      (is (nil? (get-doc hakukohde/index-name hakukohde-oid)))
      (is (nil? (get-doc toteutus/index-name toteutus-oid)))
      (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(deftest index-all-toteutukset-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index all toteutukset"
      (check-all-nil)
      (i/index-all-toteutukset)
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (nil? (get-doc hakukohde/index-name hakukohde-oid)))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (= oppilaitoksen-osa-oid (:oid (get-doc oppilaitos-search/index-name oppilaitoksen-osa-oid))))
      (is (= oppilaitoksen-osa-oid2 (:oid (get-doc oppilaitos-search/index-name oppilaitoksen-osa-oid2))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(defonce koulutus-oid-extra "1.2.246.562.13.00000000000000000099")
(defonce toteutus-oid-extra "1.2.246.562.17.00000000000000000099")
(defonce oppilaitos-oid-extra "1.2.246.562.10.77777777799")

(deftest index-all-hakukohteet-test
  (fixture/add-hakukohde-mock hakukohde-oid toteutus-oid haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid)

  (fixture/with-mocked-indexing
    (testing "Indexer should index all hakukohteet"
      (check-all-nil)
      (i/index-all-hakukohteet)
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(deftest index-hakukohde-test
  (fixture/add-koulutus-mock koulutus-oid-extra :tarjoajat oppilaitos-oid-extra)
  (fixture/add-toteutus-mock toteutus-oid-extra koulutus-oid-extra :tarjoajat oppilaitos-oid-extra)
  (fixture/add-hakukohde-mock hakukohde-oid toteutus-oid haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid)
  (fixture/add-hakukohde-mock hakukohde-oid2 toteutus-oid-extra haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid-extra)

  (fixture/with-mocked-indexing
    (testing "Indexer should index only hakukohde related koulutus and oppilaitos"
      (check-all-nil)
      (i/index-hakukohde hakukohde-oid)
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (= nil (:oid (get-doc toteutus/index-name toteutus-oid-extra))))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= nil (:oid (get-doc koulutus-search/index-name koulutus-oid-extra))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (= nil (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid-extra))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(deftest index-all-haut-test
  (fixture/add-hakukohde-mock hakukohde-oid toteutus-oid haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid)

  (fixture/with-mocked-indexing
    (testing "Indexer should index all haut"
      (check-all-nil)
      (i/index-all-haut)
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(deftest index-haku-test-2
  (fixture/add-koulutus-mock koulutus-oid-extra :sorakuvausId sorakuvaus-id :tarjoajat oppilaitos-oid-extra)
  (fixture/add-toteutus-mock toteutus-oid-extra koulutus-oid-extra :tarjoajat oppilaitos-oid-extra)
  (fixture/add-hakukohde-mock hakukohde-oid toteutus-oid haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid)
  (fixture/add-hakukohde-mock hakukohde-oid2 toteutus-oid-extra haku-oid :valintaperuste valintaperuste-id :jarjestyspaikkaOid oppilaitos-oid-extra)

  (fixture/with-mocked-indexing
    (testing "Indexer should index all haku related koulutukset and oppilaitokset"
      (check-all-nil)
      (i/index-haku haku-oid)
      (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
      (is (= toteutus-oid-extra (:oid (get-doc toteutus/index-name toteutus-oid-extra))))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (= oppilaitos-oid-extra (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid-extra))))
      (is (nil? (get-doc valintaperuste/index-name valintaperuste-id))))))

(deftest index-all-valintaperusteet-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index all valintaperusteet"
      (check-all-nil)
      (i/index-all-valintaperusteet)
      (is (nil? (get-doc haku/index-name haku-oid)))
      (is (= hakukohde-oid (:oid (get-doc hakukohde/index-name hakukohde-oid))))
      (is (nil? (get-doc toteutus/index-name toteutus-oid)))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
      (is (nil? (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (= valintaperuste-id (:id (get-doc valintaperuste/index-name valintaperuste-id)))))))

(deftest index-all-oppilaitokset-test
  (let [oppilaitos-oid999 "1.2.246.562.10.99999999999"
        oppilaitos-oid71 "1.2.246.562.10.66634895871"
        oppilaitos-oid57 "1.2.246.562.10.32506551657"
        oppilaitos-oid68 "1.2.246.562.10.39218317368"
        oppilaitos-oid89 "1.2.246.562.10.81927839589"
        oppilaitos-oid88 "1.2.246.562.10.67476956288"
        oppilaitos-oid610 "1.2.246.562.10.112212847610"
        oppilaitos-oid799 "1.2.246.562.10.77777777799"
        oppilaitos-oid410 "1.2.246.562.10.197113642410"
        oppilaitos-oid45 "1.2.246.562.10.54545454545"
        oppilaitos-oid91 "1.2.246.562.10.53670619591"
        oppilaitos-oid29 "1.2.246.562.10.54453921329"]
    (fixture/with-mocked-indexing
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid999 :oid oppilaitos-oid999})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid999)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid71 :oid oppilaitos-oid71})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid71)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid57 :oid oppilaitos-oid57})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid57)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid68 :oid oppilaitos-oid68})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid68)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid89 :oid oppilaitos-oid89})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid89)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid88 :oid oppilaitos-oid88})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid88)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid29 :oid oppilaitos-oid29})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid29)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid610 :oid oppilaitos-oid610})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid610)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid799 :oid oppilaitos-oid799})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid799)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid410 :oid oppilaitos-oid410})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid410)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid45 :oid oppilaitos-oid45})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid45)))
      (fixture/add-oppilaitos-mock
        (-> fixture/default-oppilaitos-map
            (merge {:organisaatio oppilaitos-oid91 :oid oppilaitos-oid91})
            (assoc-in [:_enrichedData :organisaatio :oid] oppilaitos-oid91)))
      (testing "Indexer should index all oppilaitokset"
        (let [all-oppilaitokset ["1.2.246.562.10.10101010101",
                                 "1.2.246.562.10.53670619591",
                                 "1.2.246.562.10.54545454545",
                                 "1.2.246.562.10.197113642410",
                                 "1.2.246.562.10.77777777799",
                                 "1.2.246.562.10.112212847610",
                                 "1.2.246.562.10.67476956288",
                                 "1.2.246.562.10.54453921329",
                                 "1.2.246.562.10.81927839589",
                                 "1.2.246.562.10.39218317368",
                                 "1.2.246.562.10.32506551657",
                                 "1.2.246.562.10.66634895871",
                                 "1.2.246.562.10.99999999999"]
              oppilaitokset-with-koulutukset ["1.2.246.562.10.54545454545"]]
          (check-all-nil)
          (i/index-all-oppilaitokset)
          (doseq [oppilaitos-oid all-oppilaitokset]
            (is (= oppilaitos-oid (:oid (get-doc oppilaitos/index-name oppilaitos-oid)))))
          (doseq [oppilaitos-oid oppilaitokset-with-koulutukset]
            (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid)))))
          (doseq [oppilaitos-oid (filter (fn [oid] (not (some #(= oid %) oppilaitokset-with-koulutukset))) all-oppilaitokset)]
            (is (nil? (get-doc oppilaitos-search/index-name oppilaitos-oid)))))))))

(deftest find-parent-oppilaitos-oid-in-hierarkia
  (testing "it should return nil when toimipiste has no oppilaitos parent in hierarkia"
    (let [hierarkia {:oid "1.2.246.562.10.10101010101"
                     :parentOid "1.2.246.562.10.10101010100"
                     :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                     :children []
                     :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
      (is (= nil (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                  "1.2.246.562.10.10101010122"
                  hierarkia
                  nil)))))

  (testing "it should return parent oppilaitos oid for toimipiste"
    (let [child-toimipiste-oid "1.2.246.562.10.10101010102"
          child-toimipiste {:oid child-toimipiste-oid
                            :parentOid "1.2.246.562.10.10101010101"
                            :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                            :children []
                            :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          hierarkia {:oid "1.2.246.562.10.10101010101"
                     :parentOid "1.2.246.562.10.10101010100"
                     :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                     :children [child-toimipiste]
                     :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= "1.2.246.562.10.10101010101" (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                                           "1.2.246.562.10.10101010102"
                                           hierarkia
                                           nil)))))

  (testing "it should return closest parent oppilaitos oid for toimipiste"
    (let [child-toimipiste-oid "1.2.246.562.10.10101010102"
          child-toimipiste {:oid child-toimipiste-oid
                            :parentOid "1.2.246.562.10.10101010101"
                            :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                            :children []
                            :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          parent-oppilaitos {:oid "1.2.246.562.10.10101010101"
                             :parentOid "1.2.246.562.10.10101010100"
                             :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                             :children [child-toimipiste]
                             :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          outer-oppilaitos {:oid "1.2.246.562.10.10101010100"
                            :parentOid "1.2.246.562.10.10101010100"
                            :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                            :children [parent-oppilaitos]
                            :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= "1.2.246.562.10.10101010101" (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                                           "1.2.246.562.10.10101010102"
                                           outer-oppilaitos
                                           nil)))))

  (testing "it should return when toimipiste is reached"
    (let [koulutustoimija-oid "1.2.246.562.10.10101010100"
          oppilaitos-oid1 "1.2.246.562.10.10101010101"
          extra-toimipiste-oid "1.2.246.562.10.10101010109"
          farmasia-toimipiste-oid "1.2.246.562.10.10101010103"
          hierarkia {:oid koulutustoimija-oid
                     :parentOid "1.2.246.562.10.00000000001"
                     :nimi {"fi" "Tanhualan Yliopisto"}
                     :organisaatiotyyppiUris ["organisaatiotyyppi_01"]
                     :children
                     [{:children
                       [{:children
                         [{:oid extra-toimipiste-oid
                           :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                                  :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                                  :en "University of Tanhuala, Faculty of Pharmacy extra"}
                           :parentOid farmasia-toimipiste-oid
                           :children []
                           :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                         :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten"
                                :fi "Tanhualan yliopisto, Farmasian tiedekunta"
                                :en "University of Tanhuala, Faculty of Pharmacy"}
                         :parentOid oppilaitos-oid1
                         :oid farmasia-toimipiste-oid
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                       :nimi {:sv "Tanhuala universitet"
                              :fi "Tanhualan Yliopisto"
                              :en "University of Tanhuala"}
                       :parentOid koulutustoimija-oid
                       :oid oppilaitos-oid1
                       :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
                     }]
      (is (= oppilaitos-oid1 (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                              extra-toimipiste-oid
                              hierarkia
                              nil)))))

  (testing "it should return the right parent oppilaitos oid for toimipiste when hierarkia tree has two oppilaitos"
    (let [koulutustoimija-oid "1.2.246.562.10.10101010100"
          oppilaitos-oid1 "1.2.246.562.10.10101010101"
          oppilaitos-oid2 "1.2.246.562.10.10101010102"
          extra-toimipiste-oid "1.2.246.562.10.10101010109"
          farmasia-toimipiste-oid "1.2.246.562.10.10101010103"
          foobar-oid "1.2.246.562.10.10101010104"
          joku-toinen-toimipiste-oid "1.2.246.562.10.10101010105"
          hierarkia {:oid koulutustoimija-oid
                     :parentOid "1.2.246.562.10.00000000001"
                     :nimi {"fi" "Tanhualan Yliopisto"}
                     :organisaatiotyyppiUris ["organisaatiotyyppi_01"]
                     :children
                     [{:children
                       [{:children
                         [{:oid extra-toimipiste-oid
                           :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                                  :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                                  :en "University of Tanhuala, Faculty of Pharmacy extra"}
                           :parentOid farmasia-toimipiste-oid
                           :children []
                           :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                         :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten"
                                :fi "Tanhualan yliopisto, Farmasian tiedekunta"
                                :en "University of Tanhuala, Faculty of Pharmacy"}
                         :parentOid oppilaitos-oid1
                         :oid farmasia-toimipiste-oid
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                       :nimi {:sv "Tanhuala universitet"
                              :fi "Tanhualan Yliopisto"
                              :en "University of Tanhuala"}
                       :parentOid koulutustoimija-oid
                       :oid oppilaitos-oid1
                       :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
                      {:children
                       [{:oid foobar-oid
                         :nimi {:sv "foo" :fi "bar" :en "foobar"}
                         :parentOid oppilaitos-oid2
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_06"]}
                        {:oid joku-toinen-toimipiste-oid
                         :nimi {:sv "Joku toinen yliopisto, toimipiste fi"
                                :fi "Joku toinen universitet, toimipiste sv"
                                :en "University of Joku toinen, toimipiste en"}
                         :parentOid oppilaitos-oid2
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                       :nimi {:sv "Joku toinen universitet"
                              :fi "Joku toinen Yliopisto"
                              :en "University of Joku toinen"}
                       :parentOid koulutustoimija-oid
                       :oid oppilaitos-oid2
                       :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
                      {:oid extra-toimipiste-oid
                       :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                              :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                              :en "University of Tanhuala, Faculty of Pharmacy extra"}
                       :parentOid farmasia-toimipiste-oid
                       :children []
                       :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                      {:children
                       [{:oid extra-toimipiste-oid
                         :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                                :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                                :en "University of Tanhuala, Faculty of Pharmacy extra"}
                         :parentOid farmasia-toimipiste-oid
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                       :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten"
                              :fi "Tanhualan yliopisto, Farmasian tiedekunta"
                              :en "University of Tanhuala, Faculty of Pharmacy"}
                       :parentOid oppilaitos-oid1
                       :oid farmasia-toimipiste-oid
                       :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                      {:oid foobar-oid
                       :nimi {:sv "foo" :fi "bar" :en "foobar"}
                       :parentOid oppilaitos-oid2
                       :children []
                       :organisaatiotyyppiUris ["organisaatiotyyppi_06"]}
                      {:oid joku-toinen-toimipiste-oid
                       :nimi {:sv "Joku toinen yliopisto, toimipiste fi"
                              :fi "Joku toinen universitet, toimipiste sv"
                              :en "University of Joku toinen, toimipiste en"}
                       :parentOid oppilaitos-oid2
                       :children []
                       :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]}]
      (is (= oppilaitos-oid1 (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                              extra-toimipiste-oid
                              hierarkia
                              nil)))
      (is (= oppilaitos-oid2 (oppilaitos/find-parent-oppilaitos-oid-in-hierarkia
                              foobar-oid
                              hierarkia
                              nil)))))
  )

(deftest fix-toimipiste-parents
  (testing "it should not add parentToimipisteOid for organisaatio that isn't toimipiste"
    (let [organisaatio {:oid "1.2.246.562.10.10101010104"
                        :parentOid  "1.2.246.562.10.10101010105"
                        :nimi {:sv "foo" :fi "bar" :en "foobar"}
                        :children []
                        :organisaatiotyyppiUris ["organisaatiotyyppi_06"]
                        :status "AKTIIVINEN"}]
      (is (= organisaatio (oppilaitos/fix-toimipiste-parents organisaatio)))))

  (testing "it should change child-toimipiste's :parentOid to oppilaitos-oid and add toimipiste-oid as :parentToimipisteOid"
    (let [toimipiste-oid "1.2.246.562.10.10101010102"
          oppilaitos-oid "1.2.246.562.10.10101010101"
          child-toimipiste-org {:oid "1.2.246.562.10.10101010103"
                                :parentOid toimipiste-oid
                                :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                :children []
                                :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          toimipiste-org {:oid toimipiste-oid
                          :parentOid oppilaitos-oid
                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                          :children [child-toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :parentOid "1.2.246.562.10.10101010100"
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          result {:oid oppilaitos-oid
                  :parentOid "1.2.246.562.10.10101010100"
                  :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                  :children [{:oid toimipiste-oid
                              :parentOid oppilaitos-oid
                              :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                              :children [{:oid "1.2.246.562.10.10101010103"
                                          :parentOid oppilaitos-oid
                                          :parentToimipisteOid toimipiste-oid
                                          :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                          :children []
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= result
             (oppilaitos/fix-toimipiste-parents oppilaitos-org)))))

  (testing "it should not add parentToimipisteOid for organisaatio that has oppilaitos as a parent"
    (let [oppilaitos-oid "1.2.246.562.10.10101010101"
          toimipiste-org {:oid "1.2.246.562.10.10101010104"
                          :parentOid oppilaitos-oid
                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                          :children []
                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :parentOid "1.2.246.562.10.10101010100"
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= oppilaitos-org
             (oppilaitos/fix-toimipiste-parents oppilaitos-org)))))

  (testing "it should not add parentToimipisteOid for nested toimipiste-organisaatio that has oppilaitos as a parent"
    (let [oppilaitos-oid "1.2.246.562.10.10101010101"
          koulutustoimija-oid "1.2.246.562.10.10101010100"
          toimipiste-org {:oid "1.2.246.562.10.10101010102"
                          :parentOid oppilaitos-oid
                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                          :children []
                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :parentOid koulutustoimija-oid
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          koulutustoimija-org {:oid koulutustoimija-oid
                               :parentOid "1.2.246.562.10.101010101010000"
                               :nimi {:sv "koulutustoimija sv" :fi "koulutustoimija fi"}
                               :children [oppilaitos-org]
                               :organisaatiotyyppiUris ["organisaatiotyyppi_01"]}]
      (is (= koulutustoimija-org
             (oppilaitos/fix-toimipiste-parents koulutustoimija-org)))))

  (testing "it should change child-toimipiste's :parentOid to oppilaitos-oid and add toimipiste-oid as :parentToimipisteOid"
    (let [toimipiste-oid "1.2.246.562.10.10101010102"
          oppilaitos-oid "1.2.246.562.10.10101010101"
          koulutustoimija-oid "1.2.246.562.10.10101010100"
          child-toimipiste-org {:oid "1.2.246.562.10.10101010103"
                                :parentOid toimipiste-oid
                                :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                :children []
                                :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          toimipiste-org {:oid toimipiste-oid
                          :parentOid oppilaitos-oid
                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                          :children [child-toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :parentOid koulutustoimija-oid
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          koulutustoimija-org {:oid koulutustoimija-oid
                               :parentOid "1.2.246.562.10.101010101010000"
                               :nimi {:sv "koulutustoimija sv" :fi "koulutustoimija fi"}
                               :children [oppilaitos-org]
                               :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          result {:oid koulutustoimija-oid
                  :parentOid "1.2.246.562.10.101010101010000"
                  :nimi {:sv "koulutustoimija sv" :fi "koulutustoimija fi"}
                  :children [{:oid oppilaitos-oid
                              :parentOid koulutustoimija-oid
                              :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                              :children [{:oid toimipiste-oid
                                          :parentOid oppilaitos-oid
                                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                                          :children [{:oid "1.2.246.562.10.10101010103"
                                                      :parentOid oppilaitos-oid
                                                      :parentToimipisteOid toimipiste-oid
                                                      :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                                      :children []
                                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]

      (is (= result
             (oppilaitos/fix-toimipiste-parents koulutustoimija-org)))))

  (testing "it should change :parentOid to oppilaitos-oid and add child-toimipiste-oid as :parentToimipisteOid for both grandchild toimipiste"
    (let [oppilaitos-oid "1.2.246.562.10.10101010101"
          child-toimipiste-oid "1.2.246.562.10.10101010104"
          grandchild-toimipiste1-oid "1.2.246.562.10.10101010105"
          grandchild-toimipiste2-oid "1.2.246.562.10.10101010107"
          grandchild-toimipiste1 {:oid grandchild-toimipiste1-oid
                                  :parentOid child-toimipiste-oid
                                  :nimi {:sv "grandchild toimipiste sv" :fi "grandchild toimipiste fi"}
                                  :children []
                                  :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          grandchild-toimipiste2 {:oid grandchild-toimipiste2-oid
                                  :parentOid child-toimipiste-oid
                                  :nimi {:sv "grandchild toimipiste2 sv"
                                         :fi "grandchild toimipiste2 fi"}
                                  :children []
                                  :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          child-toimipiste {:oid child-toimipiste-oid
                            :parentOid oppilaitos-oid
                            :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                            :children [grandchild-toimipiste1
                                       grandchild-toimipiste2]
                            :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos {:oid oppilaitos-oid
                      :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                      :children [child-toimipiste]
                      :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          result {:oid oppilaitos-oid
                  :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                  :children [{:oid child-toimipiste-oid
                              :parentOid oppilaitos-oid
                              :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                              :children [{:oid grandchild-toimipiste1-oid
                                          :parentOid oppilaitos-oid
                                          :nimi {:sv "grandchild toimipiste sv" :fi "grandchild toimipiste fi"}
                                          :children []
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                          :parentToimipisteOid child-toimipiste-oid}
                                         {:oid grandchild-toimipiste2-oid
                                          :parentOid oppilaitos-oid
                                          :nimi {:sv "grandchild toimipiste2 sv"
                                                 :fi "grandchild toimipiste2 fi"}
                                          :children []
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                          :parentToimipisteOid child-toimipiste-oid}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= result
             (oppilaitos/fix-toimipiste-parents oppilaitos)))))

  (testing "it should change :parentOid to oppilaitos-oid and add child-toimipiste-oid as :parentToimipisteOid for one grandchild toimipiste (not for other because it is not toimipiste)"
    (let [oppilaitos-oid "1.2.246.562.10.10101010101"
          child-toimipiste-oid "1.2.246.562.10.10101010104"
          grandchild-ei-toimipiste-oid "1.2.246.562.10.10101010105"
          grandchild-toimipiste-oid "1.2.246.562.10.10101010107"
          grandchild-ei-toimipiste-org {:oid grandchild-ei-toimipiste-oid
                                        :parentOid child-toimipiste-oid
                                        :nimi {:sv "grandchild ei toimipiste sv" :fi "grandchild ei toimipiste fi"}
                                        :children []
                                        :organisaatiotyyppiUris ["organisaatiotyyppi_08"]}
          grandchild-toimipiste-org {:oid grandchild-toimipiste-oid
                                     :parentOid child-toimipiste-oid
                                     :nimi {:sv "grandchild toimipiste sv"
                                            :fi "grandchild toimipiste fi"}
                                     :children []
                                     :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          child-toimipiste-org {:oid child-toimipiste-oid
                                :parentOid oppilaitos-oid
                                :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                :children [grandchild-ei-toimipiste-org
                                           grandchild-toimipiste-org]
                                :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [child-toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          result {:oid oppilaitos-oid
                  :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                  :children [{:oid child-toimipiste-oid
                              :parentOid oppilaitos-oid
                              :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                              :children [{:oid grandchild-ei-toimipiste-oid
                                          :parentOid child-toimipiste-oid
                                          :nimi {:sv "grandchild ei toimipiste sv" :fi "grandchild ei toimipiste fi"}
                                          :children []
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_08"]}
                                         {:oid grandchild-toimipiste-oid
                                          :parentOid oppilaitos-oid
                                          :nimi {:sv "grandchild toimipiste sv"
                                                 :fi "grandchild toimipiste fi"}
                                          :children []
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                          :parentToimipisteOid child-toimipiste-oid}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]
      (is (= result
             (oppilaitos/fix-toimipiste-parents oppilaitos-org)))))

  (testing "it should remove parentOid from toimipiste when its oppilaitos parent is not found from the tree"
    (let [toimipiste-oid "1.2.246.562.10.10101010101"
          child-toimipiste-oid "1.2.246.562.10.10101010102"
          child-toimipiste-org {:oid child-toimipiste-oid
                                :parentOid toimipiste-oid
                                :nimi {:sv "child toimipiste sv"
                                       :fi "child toimipiste fi"}
                                :children []
                                :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          toimipiste-org {:oid toimipiste-oid
                          :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                          :children [child-toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          result {:oid toimipiste-oid
                  :nimi {:sv "toimipiste sv" :fi "toimipiste fi"}
                  :children [{:oid child-toimipiste-oid
                              :parentToimipisteOid toimipiste-oid
                              :nimi {:sv "child toimipiste sv"
                                     :fi "child toimipiste fi"}
                              :children []
                              :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
      (is (= result
             (oppilaitos/fix-toimipiste-parents toimipiste-org)))))

  (testing "it should update parentOid and parentToimipisteOid for whole hierarkia"
    (let [koulutustoimija-oid "1.2.246.562.10.10101010100"
          oppilaitos-oid "1.2.246.562.10.10101010101"
          oppilaitos-oid2 "1.2.246.562.10.10101010102"
          child-toimipiste-oid "1.2.246.562.10.1010101010111"
          random-toimipiste-oid "1.2.246.562.10.1010101010666"
          child-toimipiste-oid2 "1.2.246.562.10.1010101010222"
          grandchild-ei-toimipiste-oid "1.2.246.562.10.10101010105"
          grandchild-toimipiste-oid "1.2.246.562.10.10101010107"
          grandchild-toimipiste-oid2 "1.2.246.562.10.10101010101222"
          grandchild-toimipiste-oid3 "1.2.246.562.10.10101010101333"
          grandchild-ei-toimipiste-org {:oid grandchild-ei-toimipiste-oid
                                        :parentOid child-toimipiste-oid
                                        :nimi {:sv "grandchild ei toimipiste sv"
                                               :fi "grandchild ei toimipiste fi"}
                                        :children []
                                        :organisaatiotyyppiUris ["organisaatiotyyppi_08"]}
          grandchild-toimipiste-org {:oid grandchild-toimipiste-oid
                                     :parentOid child-toimipiste-oid
                                     :nimi {:sv "grandchild toimipiste sv"
                                            :fi "grandchild toimipiste fi"}
                                     :children []
                                     :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          child-toimipiste-org {:oid child-toimipiste-oid
                                :parentOid oppilaitos-oid
                                :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                :children [grandchild-ei-toimipiste-org
                                           grandchild-toimipiste-org]
                                :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org {:oid oppilaitos-oid
                          :parentOid koulutustoimija-oid
                          :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                          :children [child-toimipiste-org]
                          :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          grandchild-toimipiste-org2 {:oid grandchild-toimipiste-oid2
                                      :parentOid child-toimipiste-oid2
                                      :nimi {:sv "grandchild toimipiste 2 sv"
                                             :fi "grandchild toimipiste 2 fi"}
                                      :children []
                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          grandchild-toimipiste-org3 {:oid grandchild-toimipiste-oid3
                                      :parentOid child-toimipiste-oid2
                                      :nimi {:sv "grandchild toimipiste 3 sv"
                                             :fi "grandchild toimipiste 3 fi"}
                                      :children []
                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          child-toimipiste-org2 {:oid child-toimipiste-oid2
                                 :parentOid oppilaitos-oid2
                                 :nimi {:sv "child toimipiste 2 sv" :fi "child toimipiste 2 fi"}
                                 :children [grandchild-toimipiste-org2
                                            grandchild-toimipiste-org3]
                                 :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          random-toimipiste-org {:oid random-toimipiste-oid
                                 :parentOid koulutustoimija-oid
                                 :nimi {:sv "random toimipiste sv" :fi "random toimipiste fi"}
                                 :children []
                                 :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
          oppilaitos-org2 {:oid oppilaitos-oid2
                           :parentOid koulutustoimija-oid
                           :nimi {:sv "oppilaitos 2 sv" :fi "oppilaitos 2 fi"}
                           :children [child-toimipiste-org2]
                           :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
          koulutustoimija-org {:oid koulutustoimija-oid
                               :parentOid "1.2.246.562.10.101010101010000"
                               :nimi {:sv "koulutustoimija sv" :fi "koulutustoimija fi"}
                               :children [oppilaitos-org
                                          oppilaitos-org2
                                          random-toimipiste-org]
                               :organisaatiotyyppiUris ["organisaatiotyyppi_01"]}
          result {:oid koulutustoimija-oid
                  :parentOid "1.2.246.562.10.101010101010000"
                  :nimi {:sv "koulutustoimija sv" :fi "koulutustoimija fi"}
                  :children [{:oid oppilaitos-oid
                              :parentOid koulutustoimija-oid
                              :nimi {:sv "oppilaitos sv" :fi "oppilaitos fi"}
                              :children [{:oid child-toimipiste-oid
                                          :parentOid oppilaitos-oid
                                          :nimi {:sv "child toimipiste sv" :fi "child toimipiste fi"}
                                          :children [{:oid grandchild-ei-toimipiste-oid
                                                      :parentOid child-toimipiste-oid
                                                      :nimi {:sv "grandchild ei toimipiste sv" :fi "grandchild ei toimipiste fi"}
                                                      :children []
                                                      :organisaatiotyyppiUris ["organisaatiotyyppi_08"]}
                                                     {:oid grandchild-toimipiste-oid
                                                      :parentOid oppilaitos-oid
                                                      :nimi {:sv "grandchild toimipiste sv"
                                                             :fi "grandchild toimipiste fi"}
                                                      :children []
                                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                                      :parentToimipisteOid child-toimipiste-oid}]
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
                             {:oid oppilaitos-oid2
                              :parentOid koulutustoimija-oid
                              :nimi {:sv "oppilaitos 2 sv" :fi "oppilaitos 2 fi"}
                              :children [{:oid child-toimipiste-oid2
                                          :parentOid oppilaitos-oid2
                                          :nimi {:sv "child toimipiste 2 sv" :fi "child toimipiste 2 fi"}
                                          :children [{:oid grandchild-toimipiste-oid2
                                                      :parentOid oppilaitos-oid2
                                                      :nimi {:sv "grandchild toimipiste 2 sv" :fi "grandchild toimipiste 2 fi"}
                                                      :children []
                                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                                      :parentToimipisteOid child-toimipiste-oid2}
                                                     {:oid grandchild-toimipiste-oid3
                                                      :parentOid oppilaitos-oid2
                                                      :nimi {:sv "grandchild toimipiste 3 sv"
                                                             :fi "grandchild toimipiste 3 fi"}
                                                      :children []
                                                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]
                                                      :parentToimipisteOid child-toimipiste-oid2}]
                                          :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                              :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}
                             {:oid random-toimipiste-oid
                              :nimi {:sv "random toimipiste sv" :fi "random toimipiste fi"}
                              :children []
                              :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                  :organisaatiotyyppiUris ["organisaatiotyyppi_01"]}]

      (is (= result
             (oppilaitos/fix-toimipiste-parents koulutustoimija-org)))))

  (testing "it should"
    (let [koulutustoimija-oid "1.2.246.562.10.10101010100"
          oppilaitos-oid "1.2.246.562.10.10101010101"
          extra-toimipiste-oid "1.2.246.562.10.10101010109"
          farmasia-toimipiste-oid "1.2.246.562.10.10101010102"
          ell-toimipiste-oid "1.2.246.562.10.10101010103"
          foobar-oid "1.2.246.562.10.10101010104"
          extran-lapsi-toimipiste-oid "1.2.246.562.10.1010101010999"
          hierarkia {:oid koulutustoimija-oid
                     :parentOid "1.2.246.562.10.00000000001"
                     :nimi {"fi" "Tanhualan Yliopisto"}
                     :organisaatiotyyppiUris ["organisaatiotyyppi_01"]
                     :children
                     [{:children
                       [{:children
                         [{:oid extra-toimipiste-oid
                           :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                                  :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                                  :en "University of Tanhuala, Faculty of Pharmacy extra"}
                           :parentOid farmasia-toimipiste-oid
                           :children [{:oid extran-lapsi-toimipiste-oid
                                       :nimi {:sv "Tanhuala universitet, extran lapsi"
                                              :fi "Tanhualan yliopisto, extran lapsi"
                                              :en "University of Tanhuala, extran lapsi"}
                                       :parentOid extra-toimipiste-oid
                                       :children []
                                       :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                           :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                         :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten"
                                :fi "Tanhualan yliopisto, Farmasian tiedekunta"
                                :en "University of Tanhuala, Faculty of Pharmacy"}
                         :parentOid oppilaitos-oid
                         :oid farmasia-toimipiste-oid
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                        {:oid ell-toimipiste-oid
                         :nimi {:sv "Tanhuala universitet, Veterinärmedicinska fakulteten"
                                :fi "Tanhualan yliopisto, Eläinlääketieteellinen tiedekunta"
                                :en "University of Tanhuala, Faculty of Veterinary Medicine"}
                         :parentOid oppilaitos-oid
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                        {:oid foobar-oid
                         :nimi {:sv "foo" :fi "bar" :en "foobar"}
                         :parentOid oppilaitos-oid
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_06"]}
                        {:oid "1.2.246.562.10.10101010109"
                         :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                                :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                                :en "University of Tanhuala, Faculty of Pharmacy extra"}
                         :parentOid farmasia-toimipiste-oid
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                        {:oid extran-lapsi-toimipiste-oid
                         :nimi {:sv "Tanhuala universitet, extran lapsi"
                                :fi "Tanhualan yliopisto, extran lapsi"
                                :en "University of Tanhuala, extran lapsi"}
                         :parentOid extra-toimipiste-oid
                         :children []
                         :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                       :nimi {:sv "Tanhuala universitet"
                              :fi "Tanhualan Yliopisto"
                              :en "University of Tanhuala"}
                       :parentOid koulutustoimija-oid
                       :oid oppilaitos-oid
                       :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]}
          result {:oid koulutustoimija-oid
                  :parentOid "1.2.246.562.10.00000000001"
                  :nimi {"fi" "Tanhualan Yliopisto"}
                  :organisaatiotyyppiUris ["organisaatiotyyppi_01"]
                  :children
                  [{:children
                    [{:children
                      [{:oid extra-toimipiste-oid
                        :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                               :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                               :en "University of Tanhuala, Faculty of Pharmacy extra"}
                        :parentOid oppilaitos-oid
                        :parentToimipisteOid farmasia-toimipiste-oid
                        :children [{:oid extran-lapsi-toimipiste-oid
                                    :nimi {:sv "Tanhuala universitet, extran lapsi"
                                           :fi "Tanhualan yliopisto, extran lapsi"
                                           :en "University of Tanhuala, extran lapsi"}
                                    :parentOid oppilaitos-oid
                                    :parentToimipisteOid extra-toimipiste-oid
                                    :children []
                                    :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                        :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                      :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten"
                             :fi "Tanhualan yliopisto, Farmasian tiedekunta"
                             :en "University of Tanhuala, Faculty of Pharmacy"}
                      :parentOid oppilaitos-oid
                      :oid farmasia-toimipiste-oid
                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                     {:oid ell-toimipiste-oid
                      :nimi {:sv "Tanhuala universitet, Veterinärmedicinska fakulteten"
                             :fi "Tanhualan yliopisto, Eläinlääketieteellinen tiedekunta"
                             :en "University of Tanhuala, Faculty of Veterinary Medicine"}
                      :parentOid oppilaitos-oid
                      :children []
                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                     {:oid foobar-oid
                      :nimi {:sv "foo" :fi "bar" :en "foobar"}
                      :parentOid oppilaitos-oid
                      :children []
                      :organisaatiotyyppiUris ["organisaatiotyyppi_06"]}
                     {:oid extra-toimipiste-oid
                      :nimi {:sv "Tanhuala universitet, Farmaceutiska fakulteten extra"
                             :fi "Tanhualan yliopisto, Farmasian tiedekunta extra"
                             :en "University of Tanhuala, Faculty of Pharmacy extra"}
                      :parentOid oppilaitos-oid
                      :parentToimipisteOid farmasia-toimipiste-oid
                      :children []
                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}
                     {:oid extran-lapsi-toimipiste-oid
                      :nimi {:sv "Tanhuala universitet, extran lapsi"
                             :fi "Tanhualan yliopisto, extran lapsi"
                             :en "University of Tanhuala, extran lapsi"}
                      :parentOid oppilaitos-oid
                      :parentToimipisteOid extra-toimipiste-oid
                      :children []
                      :organisaatiotyyppiUris ["organisaatiotyyppi_03"]}]
                    :nimi {:sv "Tanhuala universitet"
                           :fi "Tanhualan Yliopisto"
                           :en "University of Tanhuala"}
                    :parentOid koulutustoimija-oid
                    :oid oppilaitos-oid
                    :organisaatiotyyppiUris ["organisaatiotyyppi_02"]}]}]
      (is (= result
             (oppilaitos/fix-toimipiste-parents hierarkia))))))
