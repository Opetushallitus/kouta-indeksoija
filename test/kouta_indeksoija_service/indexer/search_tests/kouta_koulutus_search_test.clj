(ns kouta-indeksoija-service.indexer.search-tests.kouta-koulutus-search-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.rest.koodisto :refer [list-alakoodi-nimet-with-cache]]
            [kouta-indeksoija-service.indexer.tools.search :refer [deduce-koulutustyypit]]))

(defn- mock-koodiuri-fn [koodiuri]
  (fn [koodi-uri alakoodi-uri] (vector {:koodiUri koodiuri :nimi {:fi "joku nimi" :sv "joku nimi sv"}})))

(defn- mock-koodisto-koulutustyyppi
  [koodi-uri alakoodi-uri]
  (vector
   { :koodiUri "koulutustyyppi_26" :nimi {:fi "joku nimi" :sv "joku nimi sv"}}
   { :koodiUri "koulutustyyppi_4" :nimi {:fi "joku nimi2" :sv "joku nimi sv2"}}))

(deftest filter-erityisopetus-koulutustyyppi
  (testing "If not ammatillinen perustutkinto erityisopetuksena, filter out erityisopetus koulutustyyppi from koodisto response"
    (with-redefs [list-alakoodi-nimet-with-cache mock-koodisto-koulutustyyppi]
      (let [koulutus {:koulutustyyppi "amm"}
            toteutus-metadata {:ammatillinenPerustutkintoErityisopetuksena false}
            result (deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["amm" "koulutustyyppi_26"] result))))))

(deftest add-amm-erityisopetus-koulutustyyppi-koodi
  (testing "If ammatillinen perustutkinto erityisopetuksena, add only erityisopetus koulutustyyppi koodi"
    (with-redefs [list-alakoodi-nimet-with-cache mock-koodisto-koulutustyyppi]
      (let [koulutus {:koulutustyyppi "amm"}
            toteutus-metadata {:ammatillinenPerustutkintoErityisopetuksena true}
            result (deduce-koulutustyypit koulutus nil toteutus-metadata)]
        (is (= ["amm" "koulutustyyppi_4"] result))))))

(deftest add-muu-amm-tutkinto-koulutustyyppi
  (testing "If no known amm koulutuskoodi, add muu-amm-tutkinto"
    (with-redefs [list-alakoodi-nimet-with-cache (mock-koodiuri-fn "koulutustyyppi_123")]
      (let [koulutus {:koulutustyyppi "amm", :koulutuksetKoodiUri []}
            toteutus-metadata nil
            result (deduce-koulutustyypit koulutus toteutus-metadata)]
        (is (= ["amm" "muu-amm-tutkinto"] result))))))

(deftest add-tuva-normal-koulutustyyppi
  (testing "If tuva without erityisopetus, add 'tuva-normal' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "tuva"}
            toteutus-metadata {:jarjestetaanErityisopetuksena false}
            result (deduce-koulutustyypit koulutus nil toteutus-metadata)]
        (is (= ["tuva" "tuva-normal"] result)))))

(deftest add-tuva-erityisopetus-koulutustyyppi
  (testing "If tuva erityisopetuksena, add 'tuva-erityisopetus' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "tuva"}
            toteutus-metadata {:jarjestetaanErityisopetuksena true}
            result (deduce-koulutustyypit koulutus nil toteutus-metadata)]
        (is (= ["tuva" "tuva-erityisopetus"] result)))))

(deftest add-vapaa-sivistystyo-koulutustyyppi-when-opistovuosi
  (testing "If vapaa-sivistystyo-opistovuosi, add 'vapaa-sivistystyo' koulutustyyppi"
      (let [koulutus {:koulutustyyppi "vapaa-sivistystyo-opistovuosi"}
            result (deduce-koulutustyypit koulutus)]
        (is (= ["vapaa-sivistystyo" "vapaa-sivistystyo-opistovuosi"] result)))))

(deftest add-vapaa-sivistystyo-koulutustyyppi-when-muu
  (testing "If vapaa-sivistystyo-muu, add 'vapaa-sivistystyo' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "vapaa-sivistystyo-muu"}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["vapaa-sivistystyo" "vapaa-sivistystyo-muu"] result)))))

(deftest add-koulutustyyppi-when-yo-ope
  (testing "If ope-pedag-opinnot, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "ope-pedag-opinnot"}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "ope-pedag-opinnot"] result)))))

(deftest add-koulutustyyppi-when-erikoislaakari
  (testing "If erikoislaakari, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoislaakari"}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "erikoislaakari"] result)))))

(deftest add-koulutustyyppi-when-kk-opintojakso
  (testing "If kk-opintojakso, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintojakso"}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["kk-opintojakso" "kk-muu" "kk-opintojakso-normal"] result)))))

(deftest add-avoin-amk-when-avoin-amk-opintojakso
  (testing "If avoin amk-opintojakso, add 'amk-opintojakso-avoin' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintojakso"
                    :metadata {:isAvoinKorkeakoulutus true
                               :korkeakoulutustyypit [{:koulutustyyppi "amk" :tarjoajat []}]}}
          oppilaitos {:oid "1.2.246.562.10.54453921329"}
          result (deduce-koulutustyypit koulutus oppilaitos)]
      (is (= ["kk-opintojakso" "amk-opintojakso-avoin" "kk-muu" "kk-opintojakso-avoin"] result)))))

(deftest add-koulutustyyppi-when-kk-opintokokonaisuus
  (testing "If kk-opintokokonaisuus, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintokokonaisuus"}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["kk-opintokokonaisuus" "kk-muu" "kk-opintokokonaisuus-normal"] result)))))

(deftest add-yo-when-yo-opintokokonaisuus
  (testing "If yo-opintokokonaisuus, add 'yo-opintokokonaisuus' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "kk-opintokokonaisuus"
                    :metadata {:korkeakoulutustyypit [{:koulutustyyppi "yo" :tarjoajat []}]}}
          oppilaitos {:oid "1.2.246.562.10.39218317368"}
          result (deduce-koulutustyypit koulutus oppilaitos)]
      (is (= ["kk-opintokokonaisuus" "yo-opintokokonaisuus" "kk-muu" "kk-opintokokonaisuus-normal"] result)))))

(deftest add-koulutustyyppi-when-erikoistumiskoulutus
  (testing "If erikoistumiskoulutus, add 'kk-muu' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoistumiskoulutus"
                    :metadata {:korkeakoulutustyypit []}}
          result (deduce-koulutustyypit koulutus)]
      (is (= ["kk-muu" "erikoistumiskoulutus"] result)))))

(deftest add-amk-when-amk-erikoistumiskoulutus
  (testing "If amk-erikoistumiskoulutus, add 'amk-erikoistumiskoulutus' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoistumiskoulutus"
                    :metadata
                    {:korkeakoulutustyypit
                     [{:koulutustyyppi "amk" :tarjoajat ["1.2.246.562.10.54453921329"]}
                      {:koulutustyyppi "yo" :tarjoajat ["1.2.246.562.10.39218317368" "1.2.246.562.10.46312206843"]}]}}
          oppilaitos {:oid "1.2.246.562.10.54453921329"}
          result (deduce-koulutustyypit koulutus oppilaitos)]
      (is (= ["kk-muu" "erikoistumiskoulutus" "amk-erikoistumiskoulutus"] result)))))

(deftest add-yo-when-yo-erikoistumiskoulutus
  (testing "If yo-erikoistumiskoulutus, add 'yo-erikoistumiskoulutus' koulutustyyppi"
    (let [koulutus {:koulutustyyppi "erikoistumiskoulutus"
                    :metadata
                    {:korkeakoulutustyypit
                     [{:koulutustyyppi "amk" :tarjoajat ["1.2.246.562.10.54453921329"]}
                      {:koulutustyyppi "yo" :tarjoajat ["1.2.246.562.10.39218317368" "1.2.246.562.10.46312206843"]}]}}
          oppilaitos {:oid "1.2.246.562.10.39218317368"}
          result (deduce-koulutustyypit koulutus oppilaitos)]
      (is (= ["kk-muu" "erikoistumiskoulutus" "yo-erikoistumiskoulutus"] result)))))
