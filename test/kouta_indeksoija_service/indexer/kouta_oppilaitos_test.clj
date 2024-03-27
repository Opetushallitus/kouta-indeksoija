(ns kouta-indeksoija-service.indexer.kouta-oppilaitos-test
  (:require [clojure.test :refer [deftest testing is]]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]))

(def oppilaitos-response
  {:nimi
   {:fi "Aalto-yliopisto"
    :sv "Aalto-universitetet"
    :en "Aalto University"}
   :yhteystiedot
   [{:osoiteTyyppi "kaynti"
     :kieli "kieli_fi#1"
     :postinumeroUri "posti_02150"
     :postitoimipaikka "ESPOO"
     :osoite "Otakaari 1"}
    {:osoiteTyyppi "posti"
     :kieli "kieli_fi#1"
     :postinumeroUri "posti_00076#2"
     :postitoimipaikka "AALTO"
     :osoite "PL 11110"}
    {:kieli "kieli_fi#1"
     :email "hakijapalvelut@aalto.fi"}
    {:kieli "kieli_fi#1"
     :www "http://www.aalto.fi/studies"}
    {:kieli "kieli_fi#1"
     :numero "0294429290"
     :tyyppi "puhelin"}
    {:osoiteTyyppi "kaynti"
     :kieli "kieli_sv#1"
     :postinumeroUri "posti_02150"
     :postitoimipaikka "ESBO"
     :osoite "Otsvängen 1"}
    {:osoiteTyyppi "ulkomainen_kaynti"
     :kieli "kieli_en#1"
     :osoite "12 Example Street\nNortholt\nLondon\nUB5 4AS\nUK"}
    {:osoiteTyyppi "posti"
     :kieli "kieli_sv#1"
     :postinumeroUri "posti_00077#2"
     :postitoimipaikka "AALTO"
     :osoite "PB 11110"}
    {:kieli "kieli_sv#1"
     :email "ansokningsservice@aalto.fi"}
    {:kieli "kieli_sv#1"
     :www "http://www.aalto.fi/sv/studies"}
    {:kieli "kieli_sv#1"
     :numero "0294429290"
     :tyyppi "puhelin"}
    {:kieli "kieli_en#1"
     :email "admissions@aalto.fi"}
    {:osoiteTyyppi "ulkomainen_posti"
     :kieli "kieli_de#1"
     :osoite "Mozartstr. 9\n12349 Berlin\nGERMANY"}
    {:osoiteTyyppi "ulkomainen_posti"
     :kieli "kieli_en#1"
     :osoite "1 Example Street\nNortholt\nLondon\nUB5 4AS\nUK"}
    {:kieli "kieli_en#1"
     :www "http://www.aalto.fi/en/studies"}
    {:kieli "kieli_en#1"
     :numero "+358294429290"
     :tyyppi "puhelin"}]})

(def languages ["fi", "sv", "en"])

(deftest create-kielistetty-yhteystieto
  (testing "returns empty map if empty list given as a parameter"
    (is (= {}
           (oppilaitos/create-kielistetty-yhteystieto {} :email languages))))

  (testing "returns kielistetty sahkoposti with english as the only language"
    (let [sahkoposti [{:kieli "kieli_en#1" :email "admissions@aalto.fi"}]]
      (is (= {:en "admissions@aalto.fi"}
             (oppilaitos/create-kielistetty-yhteystieto sahkoposti :email languages)))))

  (testing "returns kielistetty sahkoposti with all languages"
    (let [sahkoposti [{:kieli "kieli_en#1" :email "admissions@aalto.fi"}
                      {:kieli "kieli_fi#1" :email "hakijapalvelut@aalto.fi"}
                      {:kieli "kieli_sv#1" :email "ansokningsservice@aalto.fi"}]]
      (is (= {:en "admissions@aalto.fi"
              :fi "hakijapalvelut@aalto.fi"
              :sv "ansokningsservice@aalto.fi"}
             (oppilaitos/create-kielistetty-yhteystieto sahkoposti :email languages))))))

(deftest create-kielistetty-osoitetieto
  (testing "returns kielistetty osoitetieto that has fields for postinumeroKoodiUri and (katu)osoite"
    (let [postiosoite [{:osoiteTyyppi "posti"
                        :kieli "kieli_fi#1"
                        :postinumeroUri "posti_00076"
                        :postitoimipaikka "AALTO"
                        :osoite "PL 11110"}
                       {:osoiteTyyppi "posti"
                        :kieli "kieli_sv#1"
                        :postinumeroUri "posti_00077"
                        :postitoimipaikka "AALTO"
                        :osoite "PB 11110"}]]
      (is (= {:osoite {:fi "PL 11110" :sv "PB 11110"}
              :postinumeroKoodiUri {:fi "posti_00076", :sv  "posti_00077"}}
             (oppilaitos/create-kielistetty-osoitetieto postiosoite languages))))))

(deftest create-kielistetty-osoite-str
  (testing "returns kielistetty osoite string with katuosoite, postinumero and postitoimipaikka"
    (let [postiosoite [{:osoiteTyyppi "posti"
                        :kieli "kieli_fi#1"
                        :postinumeroUri "posti_00076"
                        :postitoimipaikka "AALTO"
                        :osoite "PL 11110"}
                       {:osoiteTyyppi "posti"
                        :kieli "kieli_sv#1"
                        :postinumeroUri "posti_00076"
                        :postitoimipaikka "AALTO"
                        :osoite "PB 11110"}]]
      (is (= {:fi "PL 11110, 00076 Aalto" :sv "PB 11110, 00076 Aalto"}
             (oppilaitos/create-kielistetty-osoite-str postiosoite [] languages)))))

  (testing "returns kielistetty osoite string that doesn't have postinumero and -toimipaikka for finnish language"
    (let [postiosoite [{:osoiteTyyppi "posti"
                        :kieli "kieli_fi#1"
                        :osoite "Nakertajanraitti 1"}
                       {:osoiteTyyppi "posti"
                        :kieli "kieli_sv#1"
                        :postinumeroUri "posti_00076"
                        :postitoimipaikka "AALTO"
                        :osoite "PB 11110"}]]
      (is (= {:fi "Nakertajanraitti 1" :sv "PB 11110, 00076 Aalto"}
             (oppilaitos/create-kielistetty-osoite-str postiosoite [] languages)))))

  (testing "uses ulkomainen_posti as the value for english osoite because it doesn't exist in objects that have posti as osoiteTyyppi"
    (let [postiosoite [{:osoiteTyyppi "posti"
                        :kieli "kieli_fi#1"
                        :postinumeroUri "posti_00076"
                        :postitoimipaikka "AALTO"
                        :osoite "PL 11110"}]
          ulkomainen_posti [{:osoiteTyyppi "ulkomainen_posti"
                                   :kieli "kieli_en#1"
                                   :osoite "1 Example Street\nNortholt\nLondon\nUB5 4AS\nUK"}]]
      (is (= {:fi "PL 11110, 00076 Aalto" :en "1 Example Street, Northolt, London, UB5 4AS, UK"}
             (oppilaitos/create-kielistetty-osoite-str postiosoite ulkomainen_posti languages))))))

(deftest parse-yhteystiedot
  (testing "returns kielistetty nimi as it is in organisaatiopalveluresponse"
    (is (= {:fi "Aalto-yliopisto"
            :sv "Aalto-universitetet"
            :en "Aalto University"}
           (:nimi (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0)))))

  (testing "returns sahkoposti map with fi, en and sv language emails"
    (is (= {:fi "hakijapalvelut@aalto.fi"
            :en "admissions@aalto.fi"
            :sv "ansokningsservice@aalto.fi"}
           (:sahkoposti (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0)))))

  (testing "returns puhelinnumero map with fi, sv and en language phone numbers"
    (is (= {:fi "0294429290"
            :sv "0294429290"
            :en "+358294429290"}
           (:puhelinnumero (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0)))))

  (testing "returns postiosoite_str map with addresses for all languages"
    (is (= {:fi "PL 11110, 00076 Aalto" :sv "PB 11110, 00077 Aalto" :en "1 Example Street, Northolt, London, UB5 4AS, UK"}
           (:postiosoiteStr (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0)))))

  (testing "returns postiosoite with kielistetty osoite and postinumeroKoodiUri"
    (is (= {:osoite {:fi "PL 11110" :sv "PB 11110"}
            :postinumeroKoodiUri {:fi "posti_00076#2" :sv  "posti_00077#2"}}
           (:postiosoite (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0)))))

  (testing "returns kayntiosoite_str map with addresses for all languages"
    (is (= {:fi "Otakaari 1, 02150 Espoo" :sv "Otsvängen 1, 02150 Esbo" :en "12 Example Street, Northolt, London, UB5 4AS, UK"}
           (:kayntiosoiteStr (nth (oppilaitos/parse-yhteystiedot oppilaitos-response languages) 0))))))

(deftest create-osoite-str-for-hakijapalvelut
  (testing "creates osoite string for fi, sv and en languages"
    (is (= {:en "PO BOX 4000, 00078 Espoo" :fi "PL 4000, 00076 Espoo" :sv "PB 4000, 00077 Esbo"}
           (oppilaitos/create-osoite-str-for-hakijapalvelut {:en "PO BOX 4000" :fi "PL 4000" :sv  "PB 4000"}
                                                            {:en {:koodiUri "posti_00078#2"
                                                                  :koodiArvo "00078"
                                                                  :nimi {:en "ESPOO" :fi "ESPOO" :sv "ESBO"}}
                                                             :fi {:koodiUri "posti_00076#2"
                                                                  :koodiArvo "00076"
                                                                  :nimi {:en "ESPOO" :fi "ESPOO" :sv "ESBO"}}
                                                             :sv {:koodiUri "posti_00077#2"
                                                                  :koodiArvo "00077"
                                                                  :nimi {:en "ESPOO" :fi "ESPOO" :sv "ESBO"}}}))))

  (testing "sets finnish language postitoimipaikka as the default when postitoimipaikka doesn't have an english name"
    (is (= {:en "PO BOX 4000, 00078 Espoo" :fi "PL 4000, 00076 Espoo"}
           (oppilaitos/create-osoite-str-for-hakijapalvelut {:en "PO BOX 4000" :fi "PL 4000"}
                                                            {:fi {:koodiUri "posti_00076#2"
                                                                  :koodiArvo "00076"
                                                                  :nimi {:fi "ESPOO" :sv "ESBO"}}
                                                             :en {:koodiUri "posti_00078#2"
                                                                  :koodiArvo "00078"
                                                                  :nimi {:fi "ESPOO" :sv "ESBO"}}}))))

  (testing "doesn't add postitoimipaikka to address if address doesn't have postinumero"
    (is (= {:en "1234 Main Street, Some Town Somewhere" :fi "PL 4000, 00076 Espoo"}
           (oppilaitos/create-osoite-str-for-hakijapalvelut {:en "1234 Main Street, Some Town Somewhere" :fi "PL 4000"}
                                                            {:fi {:koodiUri "posti_00076#2"
                                                                  :koodiArvo "00076"
                                                                  :nimi {:fi "ESPOO" :sv "ESBO"}}})))))
