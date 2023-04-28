(ns kouta-indeksoija-service.indexer.search-tests.kouta-koulutus-search-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [json]]
            [kouta-indeksoija-service.indexer.tools.hakutieto :refer [get-search-hakutiedot]]))

(defn- mock-koodiuri-fn [koodiuri]
  (fn [koodi-uri alakoodi-uri] (vector {:koodiUri koodiuri :nimi {:fi "joku nimi" :sv "joku nimi sv"}})))

(defn- mock-koodisto-koulutustyyppi
  [koodi-uri alakoodi-uri]
  (vector
   { :koodiUri "koulutustyyppi_26" :nimi {:fi "joku nimi" :sv "joku nimi sv"}}
   { :koodiUri "koulutustyyppi_4" :nimi {:fi "joku nimi2" :sv "joku nimi sv2"}}))

(deftest filter-erityisopetus-koulutustyyppi
  (testing "If not ammatillinen perustutkinto erityisopetuksena, filter out erityisopetus koulutustyyppi from koodisto response"
    (with-redefs [kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache mock-koodisto-koulutustyyppi]
      (let [koulutus {:koulutustyyppi "amm"}
            toteutus-metadata {:ammatillinenPerustutkintoErityisopetuksena false}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["amm" "koulutustyyppi_26"] result))))))

(deftest add-amm-erityisopetus-koulutustyyppi-koodi
  (testing "If ammatillinen perustutkinto erityisopetuksena, add only erityisopetus koulutustyyppi koodi"
    (with-redefs [kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache mock-koodisto-koulutustyyppi]
      (let [koulutus {:koulutustyyppi "amm"}
            toteutus-metadata {:ammatillinenPerustutkintoErityisopetuksena true}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["amm" "koulutustyyppi_4"] result))))))

(deftest add-muu-amm-tutkinto-koulutustyyppi
  (testing "If no known amm koulutuskoodi, add muu-amm-tutkinto"
    (with-redefs [kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache (mock-koodiuri-fn "koulutustyyppi_123")]
      (let [koulutus {:koulutustyyppi "amm", :koulutuksetKoodiUri []}
            toteutus-metadata nil
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["amm" "muu-amm-tutkinto"] result))))))

(deftest add-tuva-normal-koulutustyyppi
  (testing "If tuva without erityisopetus, add 'tuva-normal' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "tuva"}
            toteutus-metadata {:jarjestetaanErityisopetuksena false}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["tuva" "tuva-normal"] result)))))

(deftest add-tuva-erityisopetus-koulutustyyppi
  (testing "If tuva erityisopetuksena, add 'tuva-erityisopetus' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "tuva"}
            toteutus-metadata {:jarjestetaanErityisopetuksena true}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["tuva" "tuva-erityisopetus"] result)))))

(deftest add-vapaa-sivistystyo-koulutustyyppi-when-opistovuosi
  (testing "If vapaa-sivistystyo-opistovuosi, add 'vapaa-sivistystyo' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "vapaa-sivistystyo-opistovuosi"}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
        (is (= ["vapaa-sivistystyo" "vapaa-sivistystyo-opistovuosi"] result)))))

(deftest add-vapaa-sivistystyo-koulutustyyppi-when-muu
  (testing "If vapaa-sivistystyo-muu, add 'vapaa-sivistystyo' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "vapaa-sivistystyo-muu"}
            result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
        (is (= ["vapaa-sivistystyo" "vapaa-sivistystyo-muu"] result)))))

(deftest add-kk-muu-when-yo-ope
  (testing "If ope-pedag-opinnot, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "ope-pedag-opinnot"}
          result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "ope-pedag-opinnot"] result)))))

(deftest add-kk-muu-when-erikoislaakari
  (testing "If erikoislaakari, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoislaakari"}
          result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "erikoislaakari"] result)))))

(deftest add-kk-muu-when-erikoistumiskoulutus
  (testing "If erikoistumiskoulutus, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoistumiskoulutus"}
          result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "erikoistumiskoulutus"] result)))))

(deftest add-kk-muu-when-kk-opintojakso
  (testing "If kk-opintojakso, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintojakso"}
          result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "kk-opintojakso" "kk-opintojakso-normal"] result)))))

(deftest add-kk-muu-when-kk-opintokokonaisuus
  (testing "If kk-opintokokonaisuus, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintokokonaisuus"}
          result (kouta-indeksoija-service.indexer.tools.search/deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "kk-opintokokonaisuus" "kk-opintokokonaisuus-normal"] result)))))

(deftest hakutieto-tools-test
  (let [hakuaika1     {:alkaa "2031-04-02T12:00" :paattyy "2031-05-02T12:00"}
        hakuaika2     {:alkaa "2032-04-02T12:00" :paattyy "2032-05-02T12:00"}
        hakuaika3     {:alkaa "2033-04-02T12:00" :paattyy "2033-05-02T12:00"}
        hakuaika4     {:alkaa "2034-04-02T12:00" :paattyy "2034-05-02T12:00"}

        hakutieto {:haut [{:hakuOid "1.2.246.562.29.00000000000000000001"
                           :hakutapaKoodiUri "hakutapa_02#1"
                           :hakuajat [hakuaika1 hakuaika2]
                           :hakukohteet [{:valintatapaKoodiUrit ["valintatapajono_av#1", "valintatapajono_tv#1"]
                                          :pohjakoulutusvaatimusKoodiUrit ["pohjakoulutusvaatimuskouta_122#1"]
                                          :kaytetaanHaunAikataulua true},
                                         {:valintatapaKoodiUrit ["valintatapajono_cv#1"]
                                          :kaytetaanHaunAikataulua true}]},
                          {:hakuOid "1.2.246.562.29.00000000000000000002"
                           :hakutapaKoodiUri "hakutapa_01#1"
                           :hakukohteet [{:valintatapaKoodiUrit []
                                          :pohjakoulutusvaatimusKoodiUrit ["pohjakoulutusvaatimuskouta_117#1", "pohjakoulutusvaatimuskouta_102#1"]
                                          :kaytetaanHaunAikataulua false
                                          :hakuajat [hakuaika3]},
                                         {:valintatapaKoodiUrit ["valintatapajono_cv#1", "valintatapajono_tv#1"]
                                          :kaytetaanHaunAikataulua false
                                          :hakuajat [hakuaika4]}]}
                          {:hakuOid "1.2.246.562.29.00000000000000000003"
                           :hakutapaKoodiUri "hakutapa_03#1"}]}]

    (testing "get-search-hakutiedot parses hakutiedot properly"
      (with-redefs
        [kouta-indeksoija-service.rest.koodisto/get-koodit-with-cache #(json "test/resources/koodisto/" %)
         kouta-indeksoija-service.rest.koodisto/get-alakoodit-with-cache #(json "test/resources/koodisto/alakoodit/" %)]
        (is (= (get-search-hakutiedot hakutieto)
               [{:hakuajat [{:alkaa "2031-04-02T12:00", :paattyy "2031-05-02T12:00"} {:alkaa "2032-04-02T12:00", :paattyy "2032-05-02T12:00"}], :hakutapa "hakutapa_02#1", :yhteishakuOid nil, :pohjakoulutusvaatimukset ["pohjakoulutusvaatimuskonfo_002" "pohjakoulutusvaatimuskonfo_003"], :valintatavat ["valintatapajono_av#1" "valintatapajono_tv#1"] :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2031-04-02T12:00", :paattyy "2031-05-02T12:00"} {:alkaa "2032-04-02T12:00", :paattyy "2032-05-02T12:00"}], :hakutapa "hakutapa_02#1", :yhteishakuOid nil, :pohjakoulutusvaatimukset [], :valintatavat ["valintatapajono_cv#1"] :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2033-04-02T12:00", :paattyy "2033-05-02T12:00"}], :hakutapa "hakutapa_01#1", :yhteishakuOid "1.2.246.562.29.00000000000000000002", :pohjakoulutusvaatimukset ["pohjakoulutusvaatimuskonfo_007" "pohjakoulutusvaatimuskonfo_006" "pohjakoulutusvaatimuskonfo_005"], :valintatavat [] :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2034-04-02T12:00", :paattyy "2034-05-02T12:00"}], :hakutapa "hakutapa_01#1", :yhteishakuOid "1.2.246.562.29.00000000000000000002", :pohjakoulutusvaatimukset [], :valintatavat ["valintatapajono_cv#1" "valintatapajono_tv#1"] :jarjestaaUrheilijanAmmKoulutusta nil}]))))))
