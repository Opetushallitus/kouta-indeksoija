(ns kouta-indeksoija-service.indexer.search-tests.kouta-search-index-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [common-indexer-fixture no-timestamp parse-json json read-json-as-string replace-alkamiskausi]]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.test-tools :refer [compare-json debug-pretty]]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [clj-test-utils.elasticsearch-mock-utils :refer :all]))

(use-fixtures :each common-indexer-fixture)

(defonce json-path "test/resources/search/")

(let [oppilaitos-oid1 "1.2.246.562.10.10101010199"
      oppilaitos-oid2 "1.2.246.562.10.77777777799"
      oppilaitos-oid3 "1.2.246.562.10.39218317368" ;; Helsingin Yliopisto
      oppilaitos-oid4 "1.2.246.562.10.00101010101"
      koulutus-oid1 "1.2.246.562.13.00000000000000000099"
      koulutus-oid2 "1.2.246.562.13.00000000000000000098"
      koulutus-oid3 "1.2.246.562.13.00000000000000000097"
      koulutus-oid4 "1.2.246.562.13.00000000000000000096"
      koulutus-oid5 "1.2.246.562.13.00000000000000000095"
      koulutus-oid6 "1.2.246.562.13.00000000000000000094"
      koulutus-oid7 "1.2.246.562.13.00000000000000000777"
      toteutus-oid1 "1.2.246.562.17.00000000000000000099"
      toteutus-oid2 "1.2.246.562.17.00000000000000000098"
      toteutus-oid3 "1.2.246.562.17.00000000000000000195"
      toteutus-oid4 "1.2.246.562.17.00000000000000000196"
      toteutus-oid5 "1.2.246.562.17.00000000000000000197"
      toteutus-oid6 "1.2.246.562.17.00000000000000000198"
      toteutus-oid7 "1.2.246.562.17.00000000000000000199"
      toteutus-oid8 "1.2.246.562.17.00000000000000000888"
      haku-oid1 "1.2.246.562.29.00000000000000000001"
      haku-oid2 "1.2.246.562.29.00000000000000000002"
      hakukohde-oid1 "1.2.246.562.20.00000000000000000001"
      hakukohde-oid2 "1.2.246.562.20.00000000000000000002"
      hakukohde-oid3 "1.2.246.562.20.00000000000000000003"
      valintaperuste-id1 "a5e88367-555b-4d9e-aa43-0904e5ea0a13"
      sorakuvaus-id "ffa8c6cf-a962-4bb2-bf61-fe8fc741fabd"]

  (defn- test-data-fixture
    [tests]
    (admin/initialize-indices)
    (fixture/add-oppilaitos-mock oppilaitos-oid1 :tila "julkaistu")
    (fixture/add-oppilaitos-mock oppilaitos-oid2 :tila "julkaistu")
    (fixture/add-oppilaitos-mock oppilaitos-oid3 :tila "julkaistu")
    (fixture/add-oppilaitos-mock oppilaitos-oid4 :tila "julkaistu")

    (fixture/add-koulutus-mock koulutus-oid1
                               :tila "julkaistu"
                               :nimi "Autoalan perustutkinto 0"
                               :koulutuksetKoodiUri ["koulutus_351301#1"]
                               :sorakuvausId sorakuvaus-id
                               :tarjoajat [(str oppilaitos-oid2 "2")]
                               :metadata (fixture/->keywordized-json (read-json-as-string "test/resources/search/" "koulutus-metadata")))

    (fixture/add-koulutus-mock koulutus-oid2
                               :tila "julkaistu"
                               :nimi "Hevosalan perustutkinto 0"
                               :koulutuksetKoodiUri ["koulutus_361104#1"]
                               :sorakuvausId sorakuvaus-id
                               :tarjoajat [oppilaitos-oid2]
                               :metadata (fixture/->keywordized-json (read-json-as-string "test/resources/search/" "koulutus-metadata")))

    (fixture/add-koulutus-mock koulutus-oid3
                               :tila "julkaistu"
                               :nimi "Hevosalan osaamisala"
                               :koulutustyyppi "amm-osaamisala"
                               :sorakuvausId sorakuvaus-id
                               :johtaaTutkintoon nil
                               :tarjoajat [oppilaitos-oid2]
                               :metadata fixture/amm-osaamisala-koulutus-metadata)

    (fixture/add-koulutus-mock koulutus-oid4
                               :tila "julkaistu"
                               :nimi "Hevosalan tutkinnon osat"
                               :koulutustyyppi "amm-tutkinnon-osa"
                               :johtaaTutkintoon "false"
                               :sorakuvausId sorakuvaus-id
                               :ePerusteId nil
                               :koulutuksetKoodiUri []
                               :tarjoajat [oppilaitos-oid2]
                               :metadata
                               {:tyyppi "amm-tutkinnon-osa"
                                :tutkinnonOsat [{:ePerusteId 123 :koulutusKoodiUri "koulutus_371101#1" :tutkinnonosaId 1234 :tutkinnonosaViite 5678}]
                                :kuvaus {:fi "kuvaus", :sv "kuvaus sv"}})

    (fixture/add-koulutus-mock koulutus-oid5
                               :tila "julkaistu"
                               :nimi "Hevosalan tutkinnon osat extra"
                               :koulutustyyppi "amm-tutkinnon-osa"
                               :johtaaTutkintoon "false"
                               :sorakuvausId sorakuvaus-id
                               :ePerusteId nil
                               :koulutuksetKoodiUri []
                               :tarjoajat [oppilaitos-oid1]
                               :metadata
                               {:tyyppi "amm-tutkinnon-osa"
                                :tutkinnonOsat [{:ePerusteId 123 :koulutusKoodiUri "koulutus_371101#1" :tutkinnonosaId 1234 :tutkinnonosaViite 5678}]
                                :kuvaus {:fi "kuvaus", :sv "kuvaus sv"}})

    (fixture/add-koulutus-mock koulutus-oid6
                               :tila "julkaistu"
                               :nimi "Autoalan perustutkinto xxx"
                               :koulutuksetKoodiUri ["koulutus_351301#1"]
                               :sorakuvausId sorakuvaus-id
                               :tarjoajat [oppilaitos-oid2]
                               :metadata (read-json-as-string "test/resources/search/" "koulutus-metadata"))

    (fixture/add-koulutus-mock koulutus-oid7
                               :tila "julkaistu"
                               :nimi "Osaamismerkki"
                               :ePerusteId nil
                               :koulutuksetKoodiUri []
                               :tarjoajat [oppilaitos-oid4]
                               :metadata {:linkkiEPerusteisiin {}
                                          :kuvaus {}
                                          :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_4"
                                          :opintojenLaajuusNumero 1.0
                                          :isMuokkaajaOphVirkailija true
                                          :lisatiedot []
                                          :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_001#1"]
                                          :tyyppi "vapaa-sivistystyo-osaamismerkki"
                                          :osaamismerkkiKoodiUri "osaamismerkit_1022#1"}
                               :organisaatioOid "1.2.246.562.10.00000000001"
                               :koulutustyyppi "vapaa-sivistystyo-osaamismerkki")

    (fixture/add-toteutus-mock toteutus-oid1
                               koulutus-oid2
                               :tila "julkaistu"
                               :nimi "Hevostoteutus 1"
                               :tarjoajat [(str oppilaitos-oid2 "1"), (str oppilaitos-oid2 "3")]
                               :metadata (fixture/->keywordized-json (read-json-as-string "test/resources/search/" "toteutus-metadata")))

    (fixture/add-toteutus-mock toteutus-oid2
                               koulutus-oid2
                               :tila "julkaistu"
                               :nimi "Hevostoteutus 2"
                               :tarjoajat [(str oppilaitos-oid2 "1")])

    (fixture/add-toteutus-mock toteutus-oid3
                               koulutus-oid1
                               :tila "tallennettu"
                               :nimi "Autototeutus 1"
                               :tarjoajat [(str oppilaitos-oid2 "1")])

    (fixture/add-toteutus-mock toteutus-oid4
                               koulutus-oid3
                               :tila "julkaistu"
                               :nimi "Hevostoteutus 3"
                               :tarjoajat [(str oppilaitos-oid2 "2")]
                               :metadata (fixture/->keywordized-json (read-json-as-string "test/resources/search/" "osaamisala-toteutus-metadata")))

    (fixture/add-toteutus-mock toteutus-oid5
                               koulutus-oid4
                               :tila "tallennettu"
                               :nimi "Hevostoteutus 4"
                               :tarjoajat [(str oppilaitos-oid2 "3")])

    (fixture/add-toteutus-mock toteutus-oid6
                               koulutus-oid5
                               :tila "tallennettu"
                               :nimi "Hevostoteutus yyy"
                               :tarjoajat [(str oppilaitos-oid2 "4")])

    (fixture/add-toteutus-mock toteutus-oid7
                               koulutus-oid6
                               :tila "arkistoitu"
                               :nimi "Autototeutus xxx"
                               :tarjoajat [(str oppilaitos-oid2 "24")])

    (fixture/add-toteutus-mock toteutus-oid8
                               koulutus-oid7
                               :tila "julkaistu"
                               :nimi "Osaamismerkkitoteutus"
                               :tarjoajat [(str oppilaitos-oid4 "1")]
                               :metadata (fixture/->keywordized-json (read-json-as-string "test/resources/search/" "osaamismerkkitoteutus-metadata")))

    (fixture/add-haku-mock haku-oid1
                           :tila "julkaistu"
                           :nimi "Haku 0"
                           :muokkaaja "1.2.246.562.24.62301161440"
                           :modified "2019-02-05T09:49:23")

    (fixture/add-haku-mock haku-oid2
                           :tila "julkaistu"
                           :nimi "Yhteishaku"
                           :hakutapaKoodiUri "hakutapa_01#1"
                           :muokkaaja "1.2.246.562.24.62301161440"
                           :modified "2019-02-05T09:49:23")

    (fixture/add-hakukohde-mock hakukohde-oid1
                                toteutus-oid1
                                haku-oid1
                                :tila "julkaistu"
                                :valintaperuste valintaperuste-id1
                                :nimi "Koulutuksen 0 toteutuksen 0 hakukohde 0"
                                :muokkaaja "1.2.246.562.24.62301161440"
                                :modified "2019-02-05T09:49:23")

    (fixture/add-hakukohde-mock hakukohde-oid2
                                toteutus-oid2
                                haku-oid1
                                :tila "julkaistu"
                                :valintaperuste valintaperuste-id1
                                :nimi "Koulutuksen 0 toteutuksen 1 hakukohde 0"
                                :muokkaaja "1.2.246.562.24.62301161440"
                                :modified "2019-02-05T09:49:23")

    (fixture/add-hakukohde-mock hakukohde-oid3
                                toteutus-oid2
                                haku-oid2
                                :tila "julkaistu"
                                :valintaperuste valintaperuste-id1
                                :nimi "Koulutuksen 0 toteutuksen 1 hakukohde 1"
                                :muokkaaja "1.2.246.562.24.62301161440"
                                :modified "2019-02-05T09:49:23")

    (fixture/add-sorakuvaus-mock sorakuvaus-id
                                 :tila "julkaistu"
                                 :nimi "Sorakuvaus 0"
                                 :muokkaaja "1.2.246.562.24.62301161440"
                                 :modified "2019-02-05T09:49:23")

    (fixture/add-valintaperuste-mock valintaperuste-id1
                                     :tila "julkaistu"
                                     :nimi "Valintaperuste 0"
                                     :muokkaaja "1.2.246.562.24.62301161440"
                                     :modified "2019-02-05T09:49:23")

    (tests)
    (fixture/teardown))

  (use-fixtures :each test-data-fixture)

  (deftest index-oppilaitos-search-items-test-1
    (fixture/with-mocked-indexing
      (testing "Do not index oppilaitos in search index when it has no koulutukset"
        (is (nil? (get-doc oppilaitos/index-name oppilaitos-oid1)))
        (i/index-oppilaitos oppilaitos-oid1)
        (is (= nil (:oid (get-doc oppilaitos/index-name oppilaitos-oid1)))))))

  (deftest index-oppilaitos-search-items-test-2
    (fixture/with-mocked-indexing
      (testing "Create correct search item when oppilaitos has koulutukset and toteutukset"
        (is (nil? (get-doc oppilaitos/index-name oppilaitos-oid2)))
        (i/index-oppilaitos oppilaitos-oid2)
        (compare-json (no-timestamp
                       (parse-json
                        (replace-alkamiskausi
                         (read-json-as-string json-path "oppilaitos-search-item-koulutus-and-toteutukset"))))
                      (no-timestamp (get-doc oppilaitos/index-name oppilaitos-oid2))
                      [:search_terms]
                      [:fi :nimi]))))

  (deftest index-koulutus-search-items-test-1
    (fixture/with-mocked-indexing
      (testing "Create correct search item when koulutus has no toteutukset"
        (is (nil? (get-doc koulutus-search/index-name koulutus-oid1)))
        (i/index-koulutus koulutus-oid1)
        (compare-json (no-timestamp (json json-path "koulutus-search-item-no-toteutukset"))
                      (no-timestamp (get-doc koulutus-search/index-name koulutus-oid1))
                      [:search_terms]
                      [:fi :toteutusNimi]))))

  (deftest index-koulutus-search-items-test-2
    (fixture/with-mocked-indexing
      (testing "Create correct search item when koulutus has toteutukset"
        (is (nil? (get-doc koulutus-search/index-name koulutus-oid2)))
        (i/index-koulutus koulutus-oid2)
        (i/index-oppilaitos oppilaitos-oid2)
        (compare-json (no-timestamp (json json-path "koulutus-search-item-toteutukset"))
                      (no-timestamp (get-doc koulutus-search/index-name koulutus-oid2))
                      [:search_terms]
                      [:fi :toteutusNimi]))))

  (deftest index-koulutus-search-items-test-3
    (fixture/with-mocked-indexing
      (testing "Create correct search item when amm-osaamisala"
        (is (nil? (get-doc koulutus-search/index-name koulutus-oid3)))
        (i/index-koulutus koulutus-oid3)
        (i/index-oppilaitos oppilaitos-oid2)
        ;(debug-pretty (get-doc koulutus/index-name koulutus-oid3))
        (compare-json (no-timestamp (parse-json
                                     (replace-alkamiskausi
                                      (read-json-as-string json-path "koulutus-search-item-osaamisala"))))
                      (no-timestamp (get-doc koulutus-search/index-name koulutus-oid3))
                      [:search_terms]
                      [:fi :toteutusNimi]))))

  (deftest index-koulutus-search-items-test-4
    (fixture/with-mocked-indexing
      (testing "Create correct search item when amm-tutkinnon-osa"
        (is (nil? (get-doc koulutus-search/index-name koulutus-oid4)))
        (i/index-koulutus koulutus-oid4)
        (i/index-oppilaitos oppilaitos-oid2)
        ;(debug-pretty (get-doc koulutus/index-name koulutus-oid4))
        (compare-json (no-timestamp (json json-path "koulutus-search-item-tutkinnon-osa"))
                      (no-timestamp (get-doc koulutus-search/index-name koulutus-oid4))
                      [:search_terms]
                      [:fi :toteutusNimi]))))

  (deftest index-osaamismerkki-search-item-test
    (fixture/with-mocked-indexing
      (testing "Create correct search item when osaamismerkki"
        (is (nil? (get-doc koulutus-search/index-name koulutus-oid7)))
        (i/index-koulutus koulutus-oid7)
        (i/index-oppilaitos oppilaitos-oid2)
        ;(debug-pretty (get-doc koulutus/index-name koulutus-oid7))
        (compare-json (no-timestamp (json json-path "koulutus-search-item-osaamismerkki"))
                      (no-timestamp (get-doc koulutus-search/index-name koulutus-oid7))
                      [:search_terms]
                      [:fi :toteutusNimi])))))



