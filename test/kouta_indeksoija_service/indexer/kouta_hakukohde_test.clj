(ns kouta-indeksoija-service.indexer.kouta-hakukohde-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.indexer.indexer :as i]
            [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
            [kouta-indeksoija-service.test-tools :refer [compare-json]]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]))

(use-fixtures :once fixture/reload-kouta-indexer-fixture)
(use-fixtures :each common-indexer-fixture)

(deftest index-hakukohde-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde to hakukohde index and update related indexes"
     (check-all-nil)
     (fixture/update-hakukohde-mock hakukohde-oid :metadata (assoc (:metadata (fixture/mock-get-hakukohde hakukohde-oid nil)) :jarjestaaUrheilijanAmmKoulutusta false))
     (i/index-hakukohde hakukohde-oid)
     (compare-json (no-timestamp (json "kouta-hakukohde-result"))
                   (no-timestamp (get-doc hakukohde/index-name hakukohde-oid)))
     (is (= haku-oid (:oid (get-doc haku/index-name haku-oid))))
     (is (= toteutus-oid (:oid (get-doc toteutus/index-name toteutus-oid))))
     (is (= koulutus-oid (:oid (get-doc koulutus-search/index-name koulutus-oid))))
     (is (nil? (get-doc koulutus/index-name koulutus-oid)))
     (is (nil? (:oid (get-doc oppilaitos-search/index-name oppilaitos-oid)))))))

(deftest quick-index-hakukohde-test
  (fixture/with-mocked-indexing
  (testing "Indexer should quick-index only hakukohde to hakukohde index"
    (check-all-nil)
    (i/quick-index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
    (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)
          toteutus (get-doc toteutus/index-name toteutus-oid)]
      (is (= (:oid hakukohde) hakukohde-oid))
      (is (nil? toteutus))))))

(deftest index-lukio-hakukohde-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index hakukohde to hakukohde index and update related indexes 2"
     (check-all-nil)
     (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
     (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
     (fixture/update-hakukohde-mock hakukohde-oid
                                    :metadata {:hakukohteenLinja {:painotetutArvosanat [] :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                                               :kaytetaanHaunAlkamiskautta false
                                                :koulutuksenAlkamiskausi {:alkamiskausityyppi "henkilokohtainen suunnitelma"}})
     (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
     (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
       (is (= {:painotetutArvosanat [] :painotetutArvosanatOppiaineittain [] :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}} (get-in hakukohde [:metadata :hakukohteenLinja])))))))

(deftest index-englanninkielinen-lukio-hakukohde-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index englanninkielinen lukio hakukohde to hakukohde index with both harkinnanvaraisuus flags as false"
      (check-all-nil)
      (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
      (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata (-> fixture/lk-toteutus-metadata
                                                                                   (update-in [:opetus :opetuskieliKoodiUrit]
                                                                                              (fn [_] ["oppilaitoksenopetuskieli_4#2"]))))
      (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
      (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
        (is (= (select-keys hakukohde [:salliikoHakukohdeHarkinnanvaraisuudenKysymisen :voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita])
               {:salliikoHakukohdeHarkinnanvaraisuudenKysymisen false
                :voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita false}))))))

(deftest index-monikielinen-lukio-hakukohde-test
  (fixture/with-mocked-indexing
   (testing "Indexer should index monikielinen lukio hakukohde to hakukohde index as harkinnanvarainen"
            (check-all-nil)
            (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
            (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata (-> fixture/lk-toteutus-metadata
                                                                                         (update-in [:opetus :opetuskieliKoodiUrit]
                                                                                                    (fn [_] ["oppilaitoksenopetuskieli_1#2"
                                                                                                             "oppilaitoksenopetuskieli_4#2"]))))
            (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
            (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
              (is (= (select-keys hakukohde [:salliikoHakukohdeHarkinnanvaraisuudenKysymisen :voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita])
                     {:salliikoHakukohdeHarkinnanvaraisuudenKysymisen true
                      :voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita true}))))))

(deftest index-lukio-hakukohde-painotetut-arvosanat-kaikki-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde to hakukohde index and update related indexes 2"
      (with-redefs [kouta-indeksoija-service.rest.koodisto/get-koodit-with-cache #(json "test/resources/koodisto/" %)]
        (check-all-nil)
        (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
        (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
        (fixture/update-hakukohde-mock hakukohde-oid
                                       :metadata {:hakukohteenLinja           {:painotetutArvosanat     [{:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_a1it#1"}, :painokerroin 7},
                                                                                                         {:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_a1#1"}, :painokerroin 2},
                                                                                                         {:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_a1en#1"}, :painokerroin 99},
                                                                                                         {:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_b2en#1"}, :painokerroin 5}]
                                                                               :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                                                  :kaytetaanHaunAlkamiskautta false
                                                  :koulutuksenAlkamiskausi    {:alkamiskausityyppi "henkilokohtainen suunnitelma"}})
        (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
        (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
          (is (= {:painotetutArvosanat [{:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1it#1",
                                                             :nimi {:fi "painotettavatoppiaineetlukiossa_a1it#1 nimi fi",
                                                                    :sv "painotettavatoppiaineetlukiossa_a1it#1 nimi sv"}}}, :painokerroin 7}
                                         {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1#1",
                                                              :nimi
                                                              {:fi "painotettavatoppiaineetlukiossa_a1#1 nimi fi",
                                                               :sv "painotettavatoppiaineetlukiossa_a1#1 nimi sv"}}}, :painokerroin 2}
                                         {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1en#1",
                                                              :nimi {:fi "painotettavatoppiaineetlukiossa_a1en#1 nimi fi",
                                                                     :sv "painotettavatoppiaineetlukiossa_a1en#1 nimi sv"}}}, :painokerroin 99}
                                         {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_b2en#1",
                                                              :nimi {:fi "painotettavatoppiaineetlukiossa_b2en#1 nimi fi",
                                                                     :sv "painotettavatoppiaineetlukiossa_b2en#1 nimi sv"}}}, :painokerroin 5}]
                  :painotetutArvosanatOppiaineittain     [{:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_b2en#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_b2en#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_b2en#1 nimi sv"}}}, :painokerroin 5}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1lv#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1lv#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1lv#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1vk#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1vk#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1vk#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1et#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1et#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1et#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1de#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1de#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1de#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1ru#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1ru#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1ru#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1fr#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1fr#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1fr#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1ja#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1ja#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1ja#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1lt#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1lt#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1lt#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1en#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1en#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1en#1 nimi sv"}}}, :painokerroin 99}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1zh#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1zh#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1zh#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1pt#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1pt#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1pt#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1la#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1la#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1la#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1el#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1el#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1el#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1it#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1it#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1it#1 nimi sv"}}}, :painokerroin 7}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1sv#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1sv#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1sv#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1es#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1es#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1es#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1se#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1se#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1se#1 nimi sv"}}}, :painokerroin 2}
                                                          {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1fi#1",
                                                                               :nimi     {:fi "painotettavatoppiaineetlukiossa_a1fi#1 nimi fi",
                                                                                          :sv "painotettavatoppiaineetlukiossa_a1fi#1 nimi sv"}}}, :painokerroin 2}]
                  :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                 (get-in hakukohde [:metadata :hakukohteenLinja]))))))))

(deftest index-lukio-hakukohde-no-painotetut-arvosanat-kaikki-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde to hakukohde index and update related indexes 2"
      (with-redefs [kouta-indeksoija-service.rest.koodisto/get-koodit-with-cache #(json "test/resources/koodisto/" %)]
        (check-all-nil)
        (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
        (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
        (fixture/update-hakukohde-mock hakukohde-oid
                                       :metadata {:hakukohteenLinja           {:painotetutArvosanat     [{:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_a1it#1"}, :painokerroin 666},
                                                                                                         {:koodiUrit {:oppiaine "painotettavatoppiaineetlukiossa_b2en#1"}, :painokerroin 999}]
                                                                               :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                                                  :kaytetaanHaunAlkamiskautta false
                                                  :koulutuksenAlkamiskausi    {:alkamiskausityyppi "henkilokohtainen suunnitelma"}})
        (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
        (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
          (is (= {:painotetutArvosanat                  [{:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1it#1",
                                                                              :nimi {:fi "painotettavatoppiaineetlukiossa_a1it#1 nimi fi",
                                                                                     :sv "painotettavatoppiaineetlukiossa_a1it#1 nimi sv"}}}, :painokerroin 666}
                                                         {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_b2en#1",
                                                                              :nimi {:fi "painotettavatoppiaineetlukiossa_b2en#1 nimi fi",
                                                                                     :sv "painotettavatoppiaineetlukiossa_b2en#1 nimi sv"}}}, :painokerroin 999}]
                  :painotetutArvosanatOppiaineittain  [{:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_b2en#1",
                                                                 :nimi     {:fi "painotettavatoppiaineetlukiossa_b2en#1 nimi fi",
                                                                            :sv "painotettavatoppiaineetlukiossa_b2en#1 nimi sv"}}}, :painokerroin 999}
                                                       {:koodit {:oppiaine {:koodiUri "painotettavatoppiaineetlukiossa_a1it#1",
                                                                 :nimi     {:fi "painotettavatoppiaineetlukiossa_a1it#1 nimi fi",
                                                                            :sv "painotettavatoppiaineetlukiossa_a1it#1 nimi sv"}}}, :painokerroin 666}]
                  :alinHyvaksyttyKeskiarvo 6.5 :lisatietoa {:fi "fi-str", :sv "sv-str"}}
                 (get-in hakukohde [:metadata :hakukohteenLinja]))))))))


(deftest index-hakukohde-with-hakukohdekoodiuri-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde with hakukohdekoodiuri"
      (check-all-nil)
      (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "lk" :metadata fixture/lk-koulutus-metadata)
      (fixture/update-toteutus-mock toteutus-oid :tila "tallennettu" :metadata fixture/lk-toteutus-metadata)
      (fixture/update-hakukohde-mock hakukohde-oid
                                     :hakukohdeKoodiUri "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1"
                                     :nimi {}
                                     :_enrichedData {:esitysnimi {:fi "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi fi",
                                                                  :sv "hakukohteetperusopetuksenjalkeinenyhteishaku_101#1 nimi sv"}})
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
       (is (false? (:voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita hakukohde)))
       (is (false? (:salliikoHakukohdeHarkinnanvaraisuudenKysymisen hakukohde)))))))

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

(deftest index-hakukohde-yps-eramus-mundus-test
  (fixture/with-mocked-indexing
    (testing "Indexer should index hakukohde with yps if erasmus mundus"
      (check-all-nil)
      (fixture/update-koulutus-mock koulutus-oid :koulutustyyppi "yo" :johtaaTutkintoon "true" :metadata fixture/yo-koulutus-metadata)
      (fixture/update-hakukohde-mock hakukohde-oid :tila "julkaistu" :kaytetaanHaunAlkamiskautta "false" :alkamiskausiKoodiUri "kausi_s#1" :alkamisvuosi "2020")
      (fixture/update-haku-mock haku-oid :tila "julkaistu" :kohdejoukonTarkenneKoodiUri "haunkohdejoukontarkenne_010#1")
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

(deftest delete-nil-hakukohde
  (fixture/with-mocked-indexing
    (testing "Indexer should delete hakukohde that does not exist in kouta"
      (check-all-nil)
      (i/index-hakukohteet [hakukohde-oid2] (. System (currentTimeMillis)))
      (fixture/update-hakukohde-mock hakukohde-oid2) ;;Päivitetään hakukohteen arvoksi nil
      (i/index-hakukohteet [hakukohde-oid2] (. System (currentTimeMillis)))
      (is (nil? (get-doc hakukohde/index-name hakukohde-oid2))))))

(deftest index-hakukohde-jarjestaa-urheilijan-amm-koulutusta-true
  (fixture/with-mocked-indexing
    (testing "Indexer should index jarjestaaUrheilijanAmmKoulutusta=true to hakukohde from hakukohde metadata"
      (check-all-nil)
      (fixture/update-oppilaitos-mock oppilaitos-oid :metadata {:jarjestaaUrheilijanAmmKoulutusta true})
      (fixture/update-hakukohde-mock hakukohde-oid :jarjestyspaikkaOid oppilaitos-oid)
      (fixture/update-hakukohde-mock hakukohde-oid :metadata {:jarjestaaUrheilijanAmmKoulutusta true})
      (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
      (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
        (is (true? (:jarjestaaUrheilijanAmmKoulutusta hakukohde)))))))

(deftest index-hakukohde-jarjestaa-urheilijan-amm-koulutusta-false
  (fixture/with-mocked-indexing
    (testing "Indexer should index jarjestaaUrheilijanAmmKoulutusta=false to hakukohde from hakukohde metadata"
      (check-all-nil)
      (fixture/update-oppilaitoksen-osa-mock oppilaitoksen-osa-oid :metadata {:jarjestaaUrheilijanAmmKoulutusta false})
      (fixture/update-hakukohde-mock hakukohde-oid :jarjestyspaikkaOid oppilaitos-oid)
      (fixture/update-hakukohde-mock hakukohde-oid :metadata {:jarjestaaUrheilijanAmmKoulutusta false})
      (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
      (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
        (is (false? (:jarjestaaUrheilijanAmmKoulutusta hakukohde)))))))

(deftest index-hakukohde-jarjestyspaikka-toimipiste
  (fixture/with-mocked-indexing
    (testing "Indexer should index jarjestaaUrheilijanAmmKoulutusta=false to hakukohde from hakukohde metadata"
      (check-all-nil)
      (fixture/update-hakukohde-mock hakukohde-oid :jarjestyspaikkaOid "1.2.246.562.10.777777777991")
      (i/index-hakukohteet [hakukohde-oid] (. System (currentTimeMillis)))
      (let [hakukohde (get-doc hakukohde/index-name hakukohde-oid)]
        (is (= "Oppilaitos fi 1.2.246.562.10.77777777799, Toimipiste fi 1.2.246.562.10.777777777991"
               (get-in hakukohde [:jarjestyspaikkaHierarkiaNimi :fi])))))))

(deftest hakuajat-assigned-from-haku-to-hakukohde
  (fixture/with-mocked-indexing
    (testing "Indexer should assign hakuajat from haku to hakukohde"
      (check-all-nil)
      (fixture/update-hakukohde-mock hakukohde-oid2 :tila "julkaistu")
      (i/index-hakukohteet [hakukohde-oid2] (. System (currentTimeMillis)))
      (is (= "julkaistu" (:tila (get-doc hakukohde/index-name hakukohde-oid2))))
      (= (:hakuajat (get-doc haku/index-name haku-oid))
         (:hakuajat (get-doc hakukohde/index-name hakukohde-oid2))))))
