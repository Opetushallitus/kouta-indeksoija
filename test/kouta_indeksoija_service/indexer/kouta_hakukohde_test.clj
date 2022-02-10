(ns kouta-indeksoija-service.indexer.kouta-hakukohde-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.test-tools :refer [parse compare-json debug-pretty]]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.fixture.external-services :as mocks]))

(use-fixtures :each fixture/indices-fixture)
(use-fixtures :each common-indexer-fixture)

(deftest index-hakukohde-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde to hakukohde index and update related indexes"
     (check-all-nil)
     (i/index-hakukohde hakukohde-oid)
     (compare-json (no-timestamp (json "kouta-hakukohde-result"))
                   (no-timestamp (get-doc hakukohde/index-name hakukohde-oid)))
     (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
     (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
     (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
     (is (nil? (get-doc koulutus/index-name koulutus-oid)))
     (is (nil? (:oid (get-doc oppilaitos-search/index-name mocks/Oppilaitos1)))))))

(deftest index-lukio-hakukohde-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde to hakukohde index and update related indexes"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
     (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
     (fixture/update-hakukohde-mock hakukohde-oid
                                    :metadata {:hakukohteenLinja {:painotetutArvosanat [] :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                                               :kaytetaanHaunAlkamiskautta false
                                                :koulutuksenAlkamiskausi {:alkamiskausityyppi "henkilokohtainen suunnitelma"}})
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (= {:painotetutArvosanat [] :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}} (get-in hakukohde [:metadata :hakukohteenLinja])))))))

(deftest index-hakukohde-with-hakukohdekoodiuri-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde with hakukohdekoodiuri"
      (check-all-nil)
      (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
      (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
      (fixture/update-hakukohde-mock hakukohde-oid :hakukohdeKoodiUri "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1" :nimi {})
      (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
      (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
        (is (= (:nimi hakukohde) {:fi "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi fi",
                                  :sv "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi sv"}))))))

(deftest index-hakukohde-with-koulutustyyppikoodi
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde with koulutustyyppikoodi"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutuksetKoodiUri "koulutus_222336#1")
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (= "koulutustyyppiabc_01" (:koulutustyyppikoodi hakukohde)))))))

(deftest index-hakukohde-with-ammatillinen-er-koulutustyyppikoodi
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde with er-koulutustyyppikoodi"
     (check-all-nil)
     (fixture/update-toteutus-mock toteutus-oid :metadata {:ammatillinenPerustutkintoErityisopetuksena true})
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (= "koulutustyyppi_4" (:koulutustyyppikoodi hakukohde)))))))

(deftest index-hakukohde-with-tuva-er-koulutustyyppikoodi
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde with tuva-er-koulutustyyppikoodi"
     (check-all-nil)
     (fixture/update-toteutus-mock toteutus-oid :metadata {:tyyppi "tuva" :jarjestetaanErityisopetuksena true})
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (= "koulutustyyppi_41" (:koulutustyyppikoodi hakukohde)))))))

(deftest index-hakukohde-with-passive-koulutustyyppikoodi
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde with nil koulutustyyppikoodi when it is passive"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutuksetKoodiUri "koulutus_222337#1")
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (nil? (:koulutustyyppikoodi hakukohde)))))))

(deftest index-hakukohde-without-alkamiskausi
  (fixture/with-mocked-indexing
   (testing "Koulutuksen alkamiskausi is not mandatory for haku and hakukohde. Previously yps calculation would fail if both were missing"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "yo" :metadata fixture/yo-koulutus-metadata)
     (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "true" :alkamiskausiKoodiUri "kausi_s#1" :alkamisvuosi nil)
     (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri "haunkohdejoukontarkenne_3#1" :metadata {:koulutuksenAlkamiskausi {:alkamiskausityyppi "henkilokohtainen suunnitelma"}})
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)
           yhden-paikan-saanto (:yhdenPaikanSaanto hakukohde)]
       (is (= hakukohde-oid (:oid hakukohde)))
       (is (true? (:voimassa yhden-paikan-saanto)))
       (is (= "Hakukohde on yhden paikan säännön piirissä" (:syy yhden-paikan-saanto)))))))

(deftest harkinnanvaraisuus-for-korkeakoulu
  (fixture/with-mocked-indexing
   (testing "Korkeakoulutus should never be harkinnanvarainen"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "yo" :metadata fixture/yo-koulutus-metadata)
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (false? (:onkoHarkinnanvarainenKoulutus hakukohde)))))))

(deftest index-hakukohde-hakulomakelinkki-test
  (fixture/with-mocked-indexing
   (testing "Indexer should create hakulomakeLinkki from haku oid"
     (check-all-nil)
     (fixture/update-hakukohde-mock hakukohde-oid :hakulomaketyyppi "ataru")
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (compare-json (:hakulomakeLinkki (get-doc hakukohde/index-name hakukohde-oid))
                   {:fi (str "http://localhost/hakemus/hakukohde/" hakukohde-oid "?lang=fi")
                    :sv (str "http://localhost/hakemus/hakukohde/" hakukohde-oid "?lang=sv")
                    :en (str "http://localhost/hakemus/hakukohde/" hakukohde-oid "?lang=en")}))))

(deftest index-hakukohde-haun-hakulomakelinkki-test
  (fixture/with-mocked-indexing
   (testing "Indexer should create hakulomakeLinkki from haku oid"
     (check-all-nil)
     (fixture/update-haku-mock haku-oid :hakulomaketyyppi "ataru")
     (fixture/update-hakukohde-mock hakukohde-oid :hakulomaketyyppi "ataru" :kaytetaanHaunHakulomaketta true)
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (compare-json {:fi (str "http://localhost/hakemus/haku/" haku-oid "?lang=fi")
                    :sv (str "http://localhost/hakemus/haku/" haku-oid "?lang=sv")
                    :en (str "http://localhost/hakemus/haku/" haku-oid "?lang=en")}
                   (:hakulomakeLinkki (get-doc haku/index-name haku-oid))))))

(deftest index-hakukohde-yps-haku-luonnos-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde without yps if haku luonnos"
       (check-all-nil)
       (fixture/update-haku-mock haku-oid :tila "tallennettu")
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Haku on luonnos tilassa" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= false (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-hakukohde-luonnos-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde without yps if hakukohde luonnos"
       (check-all-nil)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "tallennettu")
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Hakukohde on luonnos tilassa" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= false (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-not-korkeakoulutus-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde without yps if not korkeakoulutus"
       (check-all-nil)
       (fixture/update-haku-mock haku-oid :tila "julkaistu")
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu")
       (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "amm")
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Ei korkeakoulutus koulutusta" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= false (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-not-jatkotutkintohaku-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde without yps if not jatkotutkintohaku"
       (check-all-nil)
       (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "amk" :johtaaTutkintoon "true" :metadata fixture/amk-koulutus-metadata)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "false" :alkamiskausiKoodiUri "kausi_s#1" :alkamisvuosi "2020")
       (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri "haunkohdejoukontarkenne_1#1")
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Haun kohdejoukon tarkenne on haunkohdejoukontarkenne_1#1" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= false (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-jatkotutkintohaku-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde with yps if jatkotutkintohaku"
       (check-all-nil)
       (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "yo" :johtaaTutkintoon "true" :metadata fixture/yo-koulutus-metadata)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "false" :alkamiskausiKoodiUri "kausi_s#1" :alkamisvuosi "2020")
       (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri "haunkohdejoukontarkenne_3#1")
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Hakukohde on yhden paikan säännön piirissä" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= true (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-no-tarkenne-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde with yps if no tarkenne"
       (check-all-nil)
       (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "amk" :johtaaTutkintoon "true" :metadata fixture/amk-koulutus-metadata)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "false" :alkamiskausiKoodiUri "kausi_s#1" :alkamisvuosi "2020")
       (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri nil)
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Hakukohde on yhden paikan säännön piirissä" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= true (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-yps-haun-alkamiskausi-test
   (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde using haun alkamiskausi with yps if no tarkenne"
       (check-all-nil)
       (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "amk" :johtaaTutkintoon "true" :metadata fixture/amk-koulutus-metadata)
       (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "true")
       (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri nil)
       (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
       (is (= "Hakukohde on yhden paikan säännön piirissä" (:syy (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid)))))
       (is (= true (:voimassa (:yhdenPaikanSaanto (get-doc hakukohde/index-name hakukohde-oid))))))))

(deftest index-hakukohde-test-hakukohde-julkaistu-while-haku-not-julkaistu
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde to hakukohde index non-published when related haku not published"
     (check-all-nil)
     (i/index-hakukohteet [ei-julkaistun-haun-julkaistu-hakukohde-oid] (. System (currentTimeMillis)))
     (is (= "tallennettu" (:tila (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid)))))))

(deftest delete-non-existing-hakukohde
  (fixture/with-mocked-indexing
   (testing "Indexer should delete non-existing hakukohde from hakukohde-index"
     (check-all-nil)
     (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu")
     (fixture/update-hakukohde-mock hakukohde-oid2 :tila "tallennettu")
     (i/index-hakukohteet [hakukohde-oid hakukohde-oid2] (. System (currentTimeMillis)))
     (is (= "julkaistu" (:tila (get-doc hakukohde/index-name hakukohde-oid))))
     (is (= "tallennettu" (:tila (get-doc hakukohde/index-name hakukohde-oid2))))
     (fixture/update-hakukohde-mock hakukohde-oid2 :tila "poistettu")
     (i/index-hakukohteet [hakukohde-oid hakukohde-oid2] (. System (currentTimeMillis)))
     (is (= "julkaistu" (:tila (get-doc hakukohde/index-name hakukohde-oid))))
     (is (nil? (get-doc hakukohde/index-name hakukohde-oid2))))))

(deftest delete-non-existing-hakukohde-from-search-index
  (fixture/with-mocked-indexing
   (testing "Indexer should delete non-existing hakukohde from search index"
   (check-all-nil)
   (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "tallennettu")
   (fixture/update-hakukohde-mock hakukohde-oid2 :tila "arkistoitu")
   (fixture/update-toteutus-mock toteutus-oid2 :tila "poistettu")
   (i/index-hakukohteet [ei-julkaistun-haun-julkaistu-hakukohde-oid] (. System (currentTimeMillis)))
   (is (= "tallennettu" (:tila (get-doc haku/index-name ei-julkaistu-haku-oid))))
   (is (= "tallennettu" (:tila (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid))))
   (is (= toteutus-oid3 (:oid (get-doc toteutus/index-name toteutus-oid3))))
   (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
   (is (= true (hit-key-not-empty oppilaitos-search/index-name mocks/Oppilaitos1 :hakutiedot)))
   (fixture/update-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid :tila "poistettu")
   (fixture/update-haku-mock ei-julkaistu-haku-oid :tila "poistettu")
   (fixture/update-toteutus-mock toteutus-oid3 :tila "poistettu")
   (fixture/update-koulutus-mock koulutus-oid :tila "tallennettu")
   (i/index-haut [ei-julkaistu-haku-oid] (. System (currentTimeMillis)))
   (is (nil? (get-doc haku/index-name ei-julkaistu-haku-oid)))
   (is (nil? (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid)))
   (is (nil? (get-doc toteutus/index-name toteutus-oid3)))
   (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
   (is (= false (hit-key-not-empty oppilaitos-search/index-name mocks/Oppilaitos1 :hakutiedot))))))
