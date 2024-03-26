(ns kouta-indeksoija-service.indexer.tools.search-test
  (:require [clojure.test :refer [deftest testing is]]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [json]]
            [kouta-indeksoija-service.indexer.tools.search :as search]))

(deftest get-haun-julkaistut-hakukohteet-tests
  (testing "filters hakukohteet with tila luonnos from hakutiedot"
    (let [toteutus {:oid "1.2.246.562.13.00000000000000000009"}
          hakutiedot [{:toteutusOid "1.2.246.562.13.00000000000000000009"
                       :haut [{:hakuOid "1.2.246.562.29.00000000000000000009"
                               :tila "julkaistu",
                               :hakukohteet [{:hakukohdeOid "1.2.246.562.20.00000000000000000009"
                                              :tila "tallennettu"}]}]}]
          julkaistut-hakutiedot (search/get-toteutuksen-julkaistut-hakutiedot hakutiedot toteutus)]
      (is (empty? (:haut julkaistut-hakutiedot))))))

(deftest get-search-hakutiedot-test
  (let [hakuaika1     {:alkaa "2031-04-02T12:00" :paattyy "2031-05-02T12:00"}
        hakuaika2     {:alkaa "2032-04-02T12:00" :paattyy "2032-05-02T12:00"}
        hakuaika3     {:alkaa "2033-04-02T12:00" :paattyy "2033-05-02T12:00"}
        hakuaika4     {:alkaa "2034-04-02T12:00" :paattyy "2034-05-02T12:00"}

        hakutieto {:haut [{:hakuOid "1.2.246.562.29.00000000000000000001"
                           :hakutapaKoodiUri "hakutapa_02#1"
                           :hakuajat [hakuaika1 hakuaika2]
                           :hakukohteet [{:valintatapaKoodiUrit ["valintatapajono_av#1", "valintatapajono_tv#1"]
                                          :pohjakoulutusvaatimusKoodiUrit ["pohjakoulutusvaatimuskouta_122#1"]
                                          :kaytetaanHaunAikataulua true}
                                         {:valintatapaKoodiUrit ["valintatapajono_cv#1"]
                                          :kaytetaanHaunAikataulua true}]}
                          {:hakuOid "1.2.246.562.29.00000000000000000002"
                           :hakutapaKoodiUri "hakutapa_01#1"
                           :hakukohteet [{:valintatapaKoodiUrit []
                                          :pohjakoulutusvaatimusKoodiUrit ["pohjakoulutusvaatimuskouta_117#1", "pohjakoulutusvaatimuskouta_102#1"]
                                          :kaytetaanHaunAikataulua false
                                          :hakuajat [hakuaika3]}
                                         {:valintatapaKoodiUrit ["valintatapajono_cv#1", "valintatapajono_tv#1"]
                                          :kaytetaanHaunAikataulua false
                                          :hakuajat [hakuaika4]}]}
                          {:hakuOid "1.2.246.562.29.00000000000000000003"
                           :hakutapaKoodiUri "hakutapa_03#1"}]}]

    (testing "get-search-hakutiedot parses hakutiedot properly"
      (with-redefs
       [kouta-indeksoija-service.rest.koodisto/get-koodit-with-cache #(json "test/resources/koodisto/" %)
        kouta-indeksoija-service.rest.koodisto/get-alakoodit-with-cache #(json "test/resources/koodisto/alakoodit/" %)
        kouta-indeksoija-service.rest.koodisto/get-ylakoodit-with-cache #(vec [])]
        (is (= [{:hakuajat [{:alkaa "2031-04-02T12:00" :paattyy "2031-05-02T12:00"} {:alkaa "2032-04-02T12:00" :paattyy "2032-05-02T12:00"}]
                 :hakutapa "hakutapa_02"
                 :yhteishakuOid nil
                 :pohjakoulutusvaatimukset ["pohjakoulutusvaatimuskonfo_002" "pohjakoulutusvaatimuskonfo_003"]
                 :valintatavat ["valintatapajono_av" "valintatapajono_tv"]
                 :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2031-04-02T12:00" :paattyy "2031-05-02T12:00"} {:alkaa "2032-04-02T12:00" :paattyy "2032-05-02T12:00"}]
                 :hakutapa "hakutapa_02"
                 :yhteishakuOid nil
                 :pohjakoulutusvaatimukset []
                 :valintatavat ["valintatapajono_cv"]
                 :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2033-04-02T12:00" :paattyy "2033-05-02T12:00"}]
                 :hakutapa "hakutapa_01" :yhteishakuOid "1.2.246.562.29.00000000000000000002"
                 :pohjakoulutusvaatimukset ["pohjakoulutusvaatimuskonfo_007" "pohjakoulutusvaatimuskonfo_006" "pohjakoulutusvaatimuskonfo_005"]
                 :valintatavat []
                 :jarjestaaUrheilijanAmmKoulutusta nil}
                {:hakuajat [{:alkaa "2034-04-02T12:00" :paattyy "2034-05-02T12:00"}]
                 :hakutapa "hakutapa_01"
                 :yhteishakuOid "1.2.246.562.29.00000000000000000002"
                 :pohjakoulutusvaatimukset []
                 :valintatavat ["valintatapajono_cv" "valintatapajono_tv"]
                 :jarjestaaUrheilijanAmmKoulutusta nil}]
               (search/get-search-hakutiedot hakutieto {:hakuaika {:alkaa "2031-03-02T00:00" :paattyy "2031-04-02T00:00"}})))))))

