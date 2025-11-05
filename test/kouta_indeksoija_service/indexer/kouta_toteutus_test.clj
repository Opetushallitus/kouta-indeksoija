(ns kouta-indeksoija-service.indexer.kouta-toteutus-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [common-indexer-fixture check-all-nil no-timestamp json count-search-terms-by-key]]
            [kouta-indeksoija-service.test-tools :refer [compare-json debug-pretty]]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.fixture.external-services :as mocks]))

(use-fixtures :each common-indexer-fixture)

(deftest index-toteutus-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index toteutus to toteutus index and update related indexes"
       (check-all-nil)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu")
       (fixture/update-toteutus-mock toteutus-oid :tila "julkaistu")
       (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
       (compare-json (no-timestamp (json "kouta-toteutus-result"))
                     (no-timestamp (get-doc toteutus/index-name toteutus-oid)))
       (is (= oppilaitos-oid (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid))))
       (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
       (is (= koulutus-oid (:oid (get-doc koulutus/index-name koulutus-oid)))))))

(deftest quick-index-toteutus-test
  (fixture/with-mocked-indexing
   (testing "Indexer should quick-index toteutus to toteutus index"
     (check-all-nil)
     (i/quick-index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
       (is (= (:oid  (get-doc toteutus/index-name toteutus-oid)) toteutus-oid)))))

(deftest index-lukio-toteutus-test
    (fixture/with-mocked-indexing
     (testing "Indexer should index lukio toteutus to toteutus index"
       (with-redefs [kouta-indeksoija-service.rest.eperuste/get-doc mocks/mock-get-eperuste]
         (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
         (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
         (fixture/update-hakukohde-mock hakukohde-oid :tila "tallennettu" :metadata {:hakukohteenLinja {:painotetutArvosanat [] :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}})
         (check-all-nil)
         (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
         (compare-json (no-timestamp (json "kouta-toteutus-lukio-result"))
                       (no-timestamp (get-doc toteutus/index-name toteutus-oid)))))))

(deftest index-taiteen-perusopetus-toteutus-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index taiteen perusopetus toteutus to toteutus index"
     (check-all-nil)
     (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu")
     (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "taiteen-perusopetus" :metadata fixture/tpo-koulutus-metadata)
     (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/tpo-toteutus-metadata)
     (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
     (compare-json (no-timestamp (json "kouta-toteutus-tpo-result"))
                   (no-timestamp (get-doc toteutus/index-name toteutus-oid))))))

(deftest index-muu-toteutus-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index muu toteutus to toteutus index with correct metadata"
            (check-all-nil)
            (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :toteutusOid toteutus-oid2)
            (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "muu-koulutus" :metadata fixture/tpo-koulutus-metadata)
            (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/muu-toteutus-metadata :haut [])
            (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
            (compare-json (no-timestamp (json "kouta-toteutus-muu-result"))
                          (no-timestamp (get-doc toteutus/index-name toteutus-oid))))))

(deftest index-arkistoitu-toteutus-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index delete toteutus from search indexes when it's arkistoitu"
       (check-all-nil)
       (fixture/update-toteutus-mock toteutus-oid :tila "julkaistu")
       (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
       (is (= "julkaistu" (:tila (get-doc toteutus/index-name toteutus-oid))))
       (is (< 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid)))
       (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid)))
       (fixture/update-toteutus-mock toteutus-oid :tila "arkistoitu")
       (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
       (is (= "arkistoitu" (:tila (get-doc toteutus/index-name toteutus-oid))))
       (is (= 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid)))
       (is (= 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid))))))

(deftest delete-non-existing-toteutus
  (fixture/with-mocked-indexing
    (testing "Indexer should delete non-existing toteutus from all related indexes"
      (check-all-nil)
      (fixture/update-toteutus-mock toteutus-oid :tila "julkaistu")
      (i/index-toteutukset [toteutus-oid
                            toteutus-oid2] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc toteutus/index-name toteutus-oid))))
      (is (= "julkaistu" (:tila (get-doc toteutus/index-name toteutus-oid2))))
      (is (not (nil? (get-doc oppilaitos-search/index-name oppilaitos-oid))))
      (is (not (nil? (get-doc oppilaitos-search/index-name oppilaitoksen-osa-oid))))
      (is (not (nil? (get-doc oppilaitos-search/index-name oppilaitoksen-osa-oid2))))
      (is (< 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid)))
      (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid)))
      (is (= 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitoksen-osa-oid :toteutusOid toteutus-oid3)))
      (is (< 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid2)))
      (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid2)))
      (fixture/update-toteutus-mock toteutus-oid2 :tila "tallennettu")
      (fixture/update-toteutus-mock toteutus-oid2 :tila "poistettu")
      (i/index-toteutukset [toteutus-oid toteutus-oid2] (. System (currentTimeMillis)))
      (is (nil? (get-doc toteutus/index-name toteutus-oid2)))
      (is (= "julkaistu" (:tila (get-doc toteutus/index-name toteutus-oid))))
      (is (< 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid)))
      (is (< 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid)))
      (is (= 0 (count-search-terms-by-key koulutus-search/index-name koulutus-oid :toteutusOid toteutus-oid2)))
      (is (= 0 (count-search-terms-by-key oppilaitos-search/index-name oppilaitos-oid :toteutusOid toteutus-oid2))))))

(deftest delete-nil-toteutus
  (fixture/with-mocked-indexing
    (testing "Indexer should delete toteutus that does not exist in kouta"
      (check-all-nil)
      (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
      (fixture/update-toteutus-mock toteutus-oid) ;;Päivitetään toteutuksen arvoksi nil
      (i/index-toteutukset [toteutus-oid] (. System (currentTimeMillis)))
      (is (nil? (get-doc toteutus/index-name toteutus-oid))))))

(deftest assoc-liitetyt
  (fixture/with-mocked-indexing
    (testing "returns toteutus with two liitetty opintojakso attached"
      (let [toteutus {:tila "julkaistu"
                      :koulutusOid "1.2.246.562.13.00000000000000003145"
                      :nimi {:fi "testiopintokokonaisuustoteutus"}
                      :oid "1.2.246.562.17.00000000000000009816"
                      :metadata {:liitetytOpintojaksot ["1.2.246.562.17.00000000000000009999"
                                                        "1.2.246.562.17.00000000000000008888"]
                                 :kuvaus {:fi "<p>Opintokokonaisuuden kuvaus</p>"}
                                 :tyyppi "kk-opintokokonaisuus"}
                      :organisaatioOid "1.2.246.562.10.75204242195"}
            liitetyt-opintojaksot [{:tila "julkaistu"
                                    :koulutusOid "1.2.246.562.13.00000000000000003333"
                                    :nimi {:fi "testiopintojaksototeutus"}
                                    :oid "1.2.246.562.17.00000000000000009999"
                                    :metadata {:liitetytOpintojaksot nil
                                               :kuvaus {:fi "<p>Opintojakson kuvaus</p>"}
                                               :tyyppi "kk-opintojakso"
                                               :opintojenLaajuusNumero 7.0
                                               :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_1#1"}
                                    :organisaatioOid "1.2.246.562.10.75204242195"}
                                   {:tila "julkaistu"
                                    :koulutusOid "1.2.246.562.13.00000000000000003333"
                                    :nimi {:fi "testiopintojaksototeutus nro 2"}
                                    :oid "1.2.246.562.17.00000000000000008888"
                                    :metadata {:liitetytOpintojaksot nil
                                               :kuvaus {:fi "<p>Opintojakson nro 2 kuvaus</p>"}
                                               :tyyppi "kk-opintojakso"
                                               :opintojenLaajuusNumero 5.0
                                               :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_1#1"}
                                    :organisaatioOid "1.2.246.562.10.75204242195"}]
            enriched-toteutus {:tila "julkaistu"
                               :koulutusOid "1.2.246.562.13.00000000000000003145"
                               :nimi {:fi "testiopintokokonaisuustoteutus"}
                               :oid "1.2.246.562.17.00000000000000009816"
                               :metadata {:liitetytOpintojaksot ["1.2.246.562.17.00000000000000009999" "1.2.246.562.17.00000000000000008888"]
                                          :kuvaus {:fi "<p>Opintokokonaisuuden kuvaus</p>"}
                                          :tyyppi "kk-opintokokonaisuus"}
                               :organisaatioOid "1.2.246.562.10.75204242195"
                               :liitetytOpintojaksot [{:nimi {:fi "testiopintojaksototeutus"}
                                                       :oid "1.2.246.562.17.00000000000000009999"
                                                       :metadata {:kuvaus {:fi "<p>Opintojakson kuvaus</p>"}
                                                                  :opintojenLaajuusNumero 7.0
                                                                  :opintojenLaajuusyksikko {:koodiUri "opintojenlaajuusyksikko_1#1"
                                                                                            :nimi
                                                                                            {:sv "opintojenlaajuusyksikko_1#1 nimi sv"
                                                                                             :fi "opintojenlaajuusyksikko_1#1 nimi fi"}}}}
                                                      {:nimi {:fi "testiopintojaksototeutus nro 2"}
                                                       :oid "1.2.246.562.17.00000000000000008888"
                                                       :metadata {:kuvaus {:fi "<p>Opintojakson nro 2 kuvaus</p>"}
                                                                  :opintojenLaajuusNumero 5.0
                                                                  :opintojenLaajuusyksikko {:koodiUri "opintojenlaajuusyksikko_1#1"
                                                                                            :nimi {:sv "opintojenlaajuusyksikko_1#1 nimi sv"
                                                                                                   :fi "opintojenlaajuusyksikko_1#1 nimi fi"}}}}]}]
        (is (= enriched-toteutus (toteutus/assoc-liitetyt toteutus liitetyt-opintojaksot :liitetytOpintojaksot true)))))

    (testing "returns toteutus with one liitetty osaamismerkki attached"
      (let [toteutus {:tila "julkaistu"
                      :koulutusOid "1.2.246.562.13.00000000000000003145"
                      :nimi {:fi "testi-vst-toteutus"}
                      :oid "1.2.246.562.17.00000000000000009816"
                      :metadata {:liitetytOsaamismerkit ["1.2.246.562.17.00000000000000009999"]
                                 :kuvaus {:fi "<p>Toteutuksen kuvaus</p>"}
                                 :tyyppi "vapaa-sivistystyo-muu"}
                      :organisaatioOid "1.2.246.562.10.75204242195"}
            liitetty-osaamismerkki [{:tila "julkaistu"
                                     :nimi {:fi "Osaamismerkki: Digitaalinen tiedonhaku"
                                            :sv "Osaamismerkki: Digital informationssökning"}
                                     :oid "1.2.246.562.13.00000000000000008797"
                                     :kielivalinta ["fi" "sv"]
                                     :metadata {:linkkiEPerusteisiin {}
                                                :kuvaus {}
                                                :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_4"
                                                :opintojenLaajuusNumero 1.0
                                                :tyyppi "vapaa-sivistystyo-osaamismerkki"
                                                :osaamismerkkiKoodiUri "osaamismerkit_1022#1"}
                                     :organisaatioOid "1.2.246.562.10.00000000001"
                                     :koulutustyyppi "vapaa-sivistystyo-osaamismerkki"}]
            enriched-toteutus {:tila "julkaistu"
                               :koulutusOid "1.2.246.562.13.00000000000000003145"
                               :nimi {:fi "testi-vst-toteutus"}
                               :oid "1.2.246.562.17.00000000000000009816"
                               :metadata {:liitetytOsaamismerkit ["1.2.246.562.17.00000000000000009999"]
                                          :kuvaus {:fi "<p>Toteutuksen kuvaus</p>"}
                                          :tyyppi "vapaa-sivistystyo-muu"}
                               :organisaatioOid "1.2.246.562.10.75204242195"
                               :liitetytOsaamismerkit [{:nimi {:fi "Osaamismerkki: Digitaalinen tiedonhaku"
                                                               :sv "Osaamismerkki: Digital informationssökning"}
                                                        :oid "1.2.246.562.13.00000000000000008797"}]}]
        (is (= enriched-toteutus (toteutus/assoc-liitetyt toteutus liitetty-osaamismerkki :liitetytOsaamismerkit)))))))
