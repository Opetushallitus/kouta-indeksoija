(ns kouta-indeksoija-service.indexer.kouta-koulutus-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.test-tools :refer [compare-json]]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :as eperuste]))

(use-fixtures :each common-indexer-fixture)

(deftest index-tallennettu-koulutus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index tallennettu koulutus only to koulutus index"
      (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :tarjoajat [oppilaitos-oid])
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
      (is (nil? (get-doc oppilaitos-search/index-name oppilaitos-oid)))
      (compare-json (no-timestamp (merge (json "kouta-koulutus-result") {:tila "tallennettu"}))
                    (no-timestamp (get-doc koulutus/index-name koulutus-oid)))
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu"))))

(deftest quick-index-koulutus-test
  (fixture/with-mocked-indexing
   (testing "Indexer should quick-index koulutus to koulutus index"
     (check-all-nil)
     (i/quick-index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
     (compare-json (no-timestamp (json "kouta-koulutus-result"))
                   (no-timestamp (get-doc koulutus/index-name koulutus-oid))))))

(deftest index-julkaistu-koulutus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index julkaistu koulutus also to search index"
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (compare-json (no-timestamp (merge (json "kouta-koulutus-result") {:tila "julkaistu"}))
                    (no-timestamp (get-doc koulutus/index-name koulutus-oid))))))

(deftest index-eperuste-with-koulutus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index eperuste with koulutus"
      (let [eperuste-id 12345]
        (fixture/update-koulutus-mock koulutus-oid :ePerusteId eperuste-id)
        (check-all-nil)
        (is (nil? (eperuste/get-from-index eperuste-id)))
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
        (is (= eperuste-id (:id (eperuste/get-from-index eperuste-id))))
        (fixture/update-koulutus-mock koulutus-oid :ePerusteId nil)))))

(deftest index-arkistoitu-koulutus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should delete koulutus from search indexes when it's arkistoitu"
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :koulutusOid koulutus-oid)))
      (fixture/update-koulutus-mock koulutus-oid :tila "arkistoitu")
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (= "arkistoitu" (:tila (get-doc koulutus/index-name koulutus-oid))))
      (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
      (is (= 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :koulutusOid koulutus-oid)))
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu"))))

(deftest delete-non-existing-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should delete non-existing koulutus from index"
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc koulutus/index-name koulutus-oid))))
      (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
      (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :koulutusOid koulutus-oid)))
      (fixture/update-koulutus-mock koulutus-oid :tila "poistettu")
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc koulutus/index-name koulutus-oid)))
      (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
      (is (= 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :koulutusOid koulutus-oid))))))

(def tutkinnon-osa-koulutusala1
  {:koodiUri "kansallinenkoulutusluokitus2016koulutusalataso1_07"
   :nimi {:fi "Tekniikan alat"
          :en "Engineering, manufacturing and construction"
          :sv "De tekniska områdena"}})

(def tutkinnon-osa-koulutusala2
  {:koodiUri "kansallinenkoulutusluokitus2016koulutusalataso1_08"
   :nimi {:fi "Palvelualat"}})

(def osaamisala-koulutusala
  {:koodiUri "kansallinenkoulutusluokitus2016koulutusalataso1_09"
   :nimi {:fi "Maa- ja metsätalousalat"}})

(defn- mock-koulutusalat-taso1 [koulutusKoodiUri]
  (cond
    (= koulutusKoodiUri "koulutus_123123#1") [tutkinnon-osa-koulutusala1]
    (= koulutusKoodiUri "koulutus_123125#1") [tutkinnon-osa-koulutusala1]
    (= koulutusKoodiUri "koulutus_123444#1") [tutkinnon-osa-koulutusala2]
    (= koulutusKoodiUri "koulutus_222333#1") [osaamisala-koulutusala]))

(deftest index-amm-tutkinnon-osa-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index distinct koulutusalat for every tutkinnon osa"
      (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
        (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amm-tutkinnon-osa" :metadata fixture/amm-tutkinnon-osa-koulutus-metadata)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus/index-name koulutus-oid)
              koulutusalat (get-in koulutus [:metadata :koulutusala])]
          (is (= 2 (count koulutusalat)))
          (is (-> koulutusalat first :nimi :fi) "Tekniikan alat")
          (is (-> koulutusalat last :nimi :fi) "Palvelualat"))))))

(deftest index-amm-tutkinnon-osa-koulutus-with-eperuste-not-yet-voimassa
  (fixture/with-mocked-indexing
   (testing "Indexer should index esitysnimi as nimi for koulutus if it is defined, relevant for future eperusteet"
     (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
       (fixture/update-koulutus-mock koulutus-oid
                                     :_enrichedData fixture/amm-tutkinnon-osa-enriched-data)
       (check-all-nil)
       (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
       (let [koulutus (get-doc koulutus/index-name koulutus-oid)
             nimi-fi (get-in koulutus [:nimi :fi])]
         (is (= nimi-fi "Autoalan perustutkinto 0 fi (voimaantulo 6.6.2025)")))))))

(deftest index-amm-tutkinnon-osa-koulutus-search-with-eperuste-not-yet-voimassa
  (fixture/with-mocked-indexing
    (testing "Indexer should index esitysnimi as nimi for koulutus-search if it is defined, relevant for future eperusteet"
      (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
        (fixture/update-koulutus-mock koulutus-oid
                                      :_enrichedData fixture/amm-tutkinnon-osa-enriched-data)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus-search/index-name koulutus-oid)
              nimi-fi (get-in koulutus [:nimi :fi])]
          (is (= nimi-fi "Autoalan perustutkinto 0 fi (voimaantulo 6.6.2025)")))))))

(deftest eperuste-data-enrichment
  (fixture/with-mocked-indexing
    (testing "Indexer should enrich eperuste data to metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amk" :metadata fixture/amk-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)]
        (is (= (get-in koulutus [:metadata :eperuste :id]) 1234))
        (is (= (get-in koulutus [:metadata :eperuste :diaarinumero]) "1111-OPH-2021"))
        (is (= (get-in koulutus [:metadata :eperuste :voimassaoloLoppuu]) "2018-01-01T00:00:00"))))))

(deftest eperuste-data-enrichment-with-no-loppuu-date
  (fixture/with-mocked-indexing
    (testing "Indexer should enrich eperuste data to metadata"
      (with-redefs [kouta-indeksoija-service.indexer.cache.eperuste/get-eperuste-by-id (fn [id] {:id id :diaarinumero "1234-OPH" :voimassaoloLoppuu nil})]
        (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amk" :metadata fixture/amk-koulutus-metadata)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus/index-name koulutus-oid)]
          (is (= (get-in koulutus [:metadata :eperuste :id]) 1234))
          (is (= (get-in koulutus [:metadata :eperuste :diaarinumero]) "1234-OPH"))
          (is (= (get-in koulutus [:metadata :eperuste :voimassaoloLoppuu]) nil)))))))

(deftest tutkinnon-osa-enrichment
  (fixture/with-mocked-indexing
    (testing "Indexer should enrich tutkinnon osat from eperusteet"
      (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
        (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amm-tutkinnon-osa" :metadata fixture/amm-tutkinnon-osa-koulutus-metadata)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus/index-name koulutus-oid)
              tutkinnon-osat (get-in koulutus [:metadata :tutkinnonOsat])
              tutkinnon-osa (->> tutkinnon-osat
                                 (filter #(= (:tutkinnonosaId %) 1234))
                                 (first))]
          (is (= (:opintojenLaajuusNumero tutkinnon-osa) 50))
          (is (= (get-in tutkinnon-osa [:opintojenLaajuus :nimi :fi]) "opintojenlaajuus_50 nimi fi"))
          (is (= (get-in tutkinnon-osa [:opintojenLaajuusyksikko :nimi :fi]) "opintojenlaajuusyksikko_6 nimi fi")))))))

(deftest index-osaamisala-koulutus
  (fixture/with-mocked-indexing
    (testing "Should index koulutusala for osaamisala"
      (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
        (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amm-osaamisala" :koulutuksetKoodiUri "koulutus_222333#1" :metadata fixture/amm-osaamisala-koulutus-metadata)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus/index-name koulutus-oid)
              koulutusalat (get-in koulutus [:metadata :koulutusala])]
          (is (= (count koulutusalat) 1))
          (is (-> koulutusalat first :nimi :fi) "Maa- ja metsätalousalat"))))))

(deftest index-lukio-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index lukio specific metadata"
      (with-redefs [kouta-indeksoija-service.indexer.tools.koodisto/koulutusalat-taso1 mock-koulutusalat-taso1]
        (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "lk" :metadata fixture/lukio-koulutus-metadata)
        (check-all-nil)
        (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
        (let [koulutus (get-doc koulutus/index-name koulutus-oid)
              tutkintonimike (-> koulutus
                                 (get-in [:metadata :tutkintonimike])
                                 (first)
                                 (get-in [:nimi :fi]))
              opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
              opintojen-laajuusnumero (get-in koulutus [:metadata :opintojenLaajuusNumero])
              koulutusala (-> koulutus
                              (get-in [:metadata :koulutusala])
                              (first)
                              (get-in [:nimi :fi]))]
          (is (= tutkintonimike "tutkintonimikkeet_00001#1 nimi fi"))
          (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_2#1 nimi fi"))
          (is (= opintojen-laajuusnumero 25))
          (is (= koulutusala "kansallinenkoulutusluokitus2016koulutusalataso1_001#1 nimi fi")))))))

(deftest index-amk-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index korkeakoulu specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu" :koulutustyyppi "amk" :metadata fixture/amk-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            tutkintonimikkeet (get-in koulutus [:metadata :tutkintonimike])]
        (is (= "opintojenlaajuusyksikko_2#1" (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])))
        (is (= 27 (get-in koulutus [:metadata :opintojenLaajuusNumero])))
        (is (= 2 (count tutkintonimikkeet)))
        (is (-> tutkintonimikkeet first :nimi :fi) "tutkintonimikekk_033#1 nimi fi")
        (is (-> tutkintonimikkeet last :nimi :fi) "tutkintonimikekk_031#1 nimi fi")))))

(deftest index-tuva-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index tuva specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "tuva" :metadata fixture/tuva-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            linkki-eperusteisiin (get-in koulutus [:metadata :linkkiEPerusteisiin :fi])
            kuvaus (get-in koulutus [:metadata :kuvaus :fi])
            opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
            opintojen-laajuusnumero (get-in koulutus [:metadata :opintojenLaajuusNumero])]
        (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_8#1"))
        (is (= linkki-eperusteisiin "http://testilinkki.fi"))
        (is (= kuvaus "kuvausteksti"))
        (is (= opintojen-laajuusnumero 38))))))

(deftest index-amm-muu-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index amm-muu specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "amm-muu" :metadata fixture/amm-muu-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
            opintojen-laajuusyksikko-nimi (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
            opintojenLaajuusNumero (get-in koulutus [:metadata :opintojenLaajuusNumero])]
        (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_4#1"))
        (is (= opintojen-laajuusyksikko-nimi "opintojenlaajuusyksikko_4#1 nimi fi"))
        (is (= 11 opintojenLaajuusNumero))))

    (testing "Indexer should index 11 for opintojenLaajuusNumero in case of amm-muu"
      (let [koulutus (get-doc koulutus-search/index-name koulutus-oid)
            opintojenLaajuusNumero (get-in koulutus [:opintojenLaajuusNumero])]
        (is (= 11 opintojenLaajuusNumero))))))

(deftest index-aikuisten-perusopetus-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index aikuisten perusopetus specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "aikuisten-perusopetus" :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            linkki-eperusteisiin (get-in koulutus [:metadata :linkkiEPerusteisiin :fi])
            kuvaus (get-in koulutus [:metadata :kuvaus :fi])
            opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
            opintojen-laajuusyksikko-nimi (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
            opintojenLaajuusNumero (get-in koulutus [:metadata :opintojenLaajuusNumero])]
        (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_2#1"))
        (is (= opintojen-laajuusyksikko-nimi "opintojenlaajuusyksikko_2#1 nimi fi"))
        (is (= 13 opintojenLaajuusNumero))
        (is (= linkki-eperusteisiin "http://testilinkki.fi"))
        (is (= kuvaus "kuvausteksti"))))

   (testing "Indexer should index 13 for opintojenLaajuusNumero in case of aikuisten-perusopetus"
     (let [koulutus (get-doc koulutus-search/index-name koulutus-oid)
           opintojenLaajuusNumero (get-in koulutus [:opintojenLaajuusNumero])]
       (is (= 13 opintojenLaajuusNumero))))))

(deftest delete-nil-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should delete koulutus that does not exist in kouta"
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (fixture/update-koulutus-mock koulutus-oid) ;;Päivitetään koulutuksen arvoksi nil
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc koulutus/index-name koulutus-oid))))))

(deftest index-kk-opintojakso-koulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index kk-opintojakso specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "kk-opintojakso" :metadata fixture/kk-opintojakso-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
            opintojen-laajuusyksikko-nimi (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
            opintojenLaajuusNumeroMin (get-in koulutus [:metadata :opintojenLaajuusNumeroMin])
            opintojenLaajuusNumeroMax (get-in koulutus [:metadata :opintojenLaajuusNumeroMax])]
        (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_2#1"))
        (is (= opintojen-laajuusyksikko-nimi "opintojenlaajuusyksikko_2#1 nimi fi"))
        (is (= 14 opintojenLaajuusNumeroMin))
        (is (= 15 opintojenLaajuusNumeroMax))))))

(deftest index-erikoistumiskoulutus
  (fixture/with-mocked-indexing
   (testing "Indexer should index erikoistumiskoulutus specific metadata"
     (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "erikoistumiskoulutus" :metadata fixture/erikoistumiskoulutus-metadata)
     (check-all-nil)
     (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
     (let [koulutus (get-doc koulutus/index-name koulutus-oid)
           erikoistumiskoulutus (get-in koulutus [:metadata :erikoistumiskoulutus :koodiUri])
           opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
           opintojen-laajuusyksikko-nimi (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
           opintojenLaajuusNumeroMin (get-in koulutus [:metadata :opintojenLaajuusNumeroMin])
           opintojenLaajuusNumeroMax (get-in koulutus [:metadata :opintojenLaajuusNumeroMax])]
       (is (= erikoistumiskoulutus "erikoistumiskoulutukset_001#2"))
       (is (= opintojen-laajuusyksikko "opintojenlaajuusyksikko_2#1"))
       (is (= opintojen-laajuusyksikko-nimi "opintojenlaajuusyksikko_2#1 nimi fi"))
       (is (= 5 opintojenLaajuusNumeroMin))
       (is (= 10 opintojenLaajuusNumeroMax))))))

(deftest index-osaamismerkkikoulutus
  (fixture/with-mocked-indexing
    (testing "Indexer should index osaamismerkki specific metadata"
      (fixture/update-koulutus-mock koulutus-oid :tila "julkaistu" :johtaaTutkintoon "false" :koulutustyyppi "vapaa-sivistystyo-osaamismerkki" :metadata fixture/osaamismerkki-koulutus-metadata)
      (check-all-nil)
      (i/index-koulutukset [koulutus-oid] (. System (currentTimeMillis)))
      (let [koulutus (get-doc koulutus/index-name koulutus-oid)
            osaamismerkki (get-in koulutus [:metadata :osaamismerkki])
            opintojen-laajuusyksikko (get-in koulutus [:metadata :opintojenLaajuusyksikko :koodiUri])
            opintojen-laajuusyksikko-nimi (get-in koulutus [:metadata :opintojenLaajuusyksikko :nimi :fi])
            opintojen-laajuusnumero (get-in koulutus [:metadata :opintojenLaajuusNumero])]
        (is (= "osaamismerkit_1022#2" osaamismerkki))
        (is (= "opintojenlaajuusyksikko_4" opintojen-laajuusyksikko))
        (is (= "opintojenlaajuusyksikko_4 nimi fi" opintojen-laajuusyksikko-nimi))
        (is (= opintojen-laajuusnumero 1))))))
