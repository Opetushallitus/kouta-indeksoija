(ns kouta-indeksoija-service.indexer.kouta-oppilaitos-search-test
  (:require [clojure.test :refer [deftest testing is]]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]))

(defn- mock-list-alakoodi-nimet
  [_ alakoodi-uri]
  (vector
   {:koodiUri (str alakoodi-uri "_1") :nimi {:fi (str alakoodi-uri "_1 fi") :sv (str alakoodi-uri "_1 sv")}}
   {:koodiUri (str alakoodi-uri "_2") :nimi {:fi (str alakoodi-uri "_2 fi") :sv (str alakoodi-uri "_2 sv")}}))

(defn- mock-get-ylakoodisto
  [_]
  [{:koodisto {:koodistoUri "kansallinenkoulutusluokitus2016koulutusalataso1"}
    :koodiUri "ylataso1uri"}
   {:koodisto {:koodistoUri "kansallinenkoulutusluokitus2016koulutusalataso2"}
    :koodiUri "ylataso2uri"}
   {:koodisto {:koodistoUri "kansallinenkoulutusluokitus2016koulutusalataso3"}
    :koodiUri "ylataso3uri"}])

(defn- mock-get-koodi-nimi
  [koodi-uri]
  {:koodiUri koodi-uri :nimi {:fi (str koodi-uri " fi") :sv (str koodi-uri " sv")}})

(def oppilaitos
  {:children [{:oid "1.2.246.562.10.50472236657"
               :status "AKTIIVINEN"
               :organisaatiotyypit ["organisaatiotyyppi_03"]
               :parentOid "1.2.246.562.10.31756159625"
               :nimi {:fi "Opisto toimipiste"
                      :sv "Opisto toimipiste"
                      :en "Opisto toimipiste"}
               :kotipaikkaUri "kunta_086"
               :kieletUris ["oppilaitoksenopetuskieli_1#1"]}]
   :kieletUris ["oppilaitoksenopetuskieli_1#1"]
   :organisaatiotyypit ["organisaatiotyyppi_02"]
   :parentOid "1.2.246.562.10.69445412302"
   :nimi {:sv "Opisto oppilaitos"
          :fi "Opisto oppilaitos"
          :en "Opisto oppilaitos"}
   :oid "1.2.246.562.10.31756159625"
   :oppilaitostyyppi "oppilaitostyyppi_63#1"
   :status "AKTIIVINEN"
   :kotipaikkaUri "kunta_086"})

(def koulutus
  {:tila "julkaistu"
   :johtaaTutkintoon false
   :teemakuva "https://konfo-files.opintopolku.fi/koulutus-teemakuva/1.2.246.562.13.00000000000000002123/08e7d481-e5f5-432e-bbd7-9272be936ddc.jpg"
   :tarjoajat ["1.2.246.562.10.31756159625"]
   :esikatselu false
   :modified "2022-11-02T15:24:05"
   :koulutuksetKoodiUri ["koulutus_999909#1"]
   :nimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"
          :sv "Folkhögskoleåret för läropliktiga"}
   :muokkaaja "1.2.246.562.24.16027275695"
   :oid "1.2.246.562.13.00000000000000002123"
   :_enrichedData {:muokkaajanNimi "Minna Muokkaaja"}
   :kielivalinta ["fi" "sv"]
   :julkinen true
   :metadata {:tyyppi "vapaa-sivistystyo-opistovuosi"
              :lisatiedot []
              :kuvaus {:fi "Kuvaus fi"
                       :sv "Kuvaus sv"}
              :linkkiEPerusteisiin {:fi "https://eperusteet.opintopolku.fi/#/fi/vapaasivistystyo/7512390/tiedot"
                                    :sv "https://eperusteet.opintopolku.fi/#/sv/vapaasivistystyo/7512390/tiedot"}
              :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_001#1"]
              :opintojenLaajuusNumero 53.0
              :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
              :isMuokkaajaOphVirkailija true}
   :organisaatioOid "1.2.246.562.10.00000000001"
   :koulutustyyppi "vapaa-sivistystyo-opistovuosi"})

(def toteutus
  {:tila "julkaistu"
   :teemakuva "https://konfo-files.opintopolku.fi/toteutus-teemakuva/1.2.246.562.17.00000000000000010979/ed43e1d1-7aa5-4b2b-a4b5-6d20581eeffb.jpg"
   :tarjoajat ["1.2.246.562.10.31756159625" "1.2.246.562.10.50472236657"]
   :esikatselu false
   :koulutusOid "1.2.246.562.13.00000000000000002123"
   :koulutuksetKoodiUri []
   :nimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"}
   :muokkaaja "1.2.246.562.24.29407417604"
   :oid "1.2.246.562.17.00000000000000010979"
   :_enrichedData {:esitysnimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"}}
   :kielivalinta ["fi"]
   :metadata {:kuvaus {:fi "Kuvaus"}
              :isMuokkaajaOphVirkailija false
              :ammattinimikkeet []
              :asiasanat [{:kieli "fi" :arvo "oppivelvollinen"}
                          {:kieli "fi" :arvo "opistovuosi oppivelvollisille"}]
              :isTyovoimakoulutus false
              :hasJotpaRahoitus false
              :isTaydennyskoulutus false
              :isPieniOsaamiskokonaisuus true
              :opetus {:opetustapaKoodiUrit ["opetuspaikkakk_1#1"]
                       :maksullisuustyyppi "maksuton" :maksullisuusKuvaus {:fi "Maksullisuuskuvaus"}
                       :opetuskieliKoodiUrit ["oppilaitoksenopetuskieli_1#2"]
                       :suunniteltuKestoKuvaus {:fi "Keston kuvaus"}
                       :koulutuksenAlkamiskausi {:alkamiskausityyppi "tarkka alkamisajankohta"
                                                 :henkilokohtaisenSuunnitelmanLisatiedot {}
                                                 :koulutuksenAlkamispaivamaara "2023-08-13T10:00"
                                                 :koulutuksenPaattymispaivamaara "2024-05-31T12:00"}
                       :suunniteltuKestoVuodet 1
                       :onkoApuraha false
                       :opetusaikaKoodiUrit ["opetusaikakk_1#1"]
                       :lisatiedot []
                       :suunniteltuKestoKuukaudet 0
                       :opetustapaKuvaus {:fi "Opetustapakuvaus"}
                       :opetuskieletKuvaus {:fi "Opetuskielenä on suomi."}
                       :opetusaikaKuvaus {:fi "Opetusaikakuvaus"}}
              :yhteyshenkilot [{:nimi {:fi "Minna Muokkaaja"}
                                :titteli {:fi "Linjavastaava"}
                                :sahkoposti {:fi "minna.muokkaaja@opisto.fi"}
                                :puhelinnumero {:fi "044 555 5555"}
                                :wwwSivu {:fi "http://www.opisto.fi"}
                                :wwwSivuTeksti {}}]
              :tyyppi "vapaa-sivistystyo-opistovuosi"}
   :organisaatioOid "1.2.246.562.10.31756159625"})

(def koulutus-search-terms-result
  {:sijainti ["kunta_086" "maakunta_1"]
   :koulutustyypit ["vapaa-sivistystyo" "vapaa-sivistystyo-opistovuosi"]
   :lukiopainotukset []
   :koulutus_organisaationimi {:fi "Opisto oppilaitos"
                               :sv "Opisto oppilaitos"
                               :en "Opisto oppilaitos"}
   :opetuskielet ["oppilaitoksenopetuskieli_1"]
   :lukiolinjaterityinenkoulutustehtava []
   :osaamisalat []
   :koulutusOid "1.2.246.562.13.00000000000000002123"
   :nimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"
          :sv "Folkhögskoleåret för läropliktiga"}
   :koulutusalat ["kansallinenkoulutusluokitus2016koulutusalataso2_001" "ylataso1uri" "ylataso2uri"]
   :kuva "https://konfo-files.opintopolku.fi/koulutus-teemakuva/1.2.246.562.13.00000000000000002123/08e7d481-e5f5-432e-bbd7-9272be936ddc.jpg"
   :onkoTuleva true
   :oppilaitosOid "1.2.246.562.10.31756159625"
   :isTyovoimakoulutus false
   :hasJotpaRahoitus false
   :isTaydennyskoulutus false
   :isPieniOsaamiskokonaisuus false
   :koulutusnimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"
                  :sv "Folkhögskoleåret för läropliktiga"}
   :koulutusKuvaus {:fi "Kuvaus fi"
                    :sv "Kuvaus sv"}
   :metadata {:koulutustyypit []
              :opintojenLaajuusyksikko
              {:koodiUri "opintojenlaajuusyksikko_2#1"
               :nimi
               {:sv "opintojenlaajuusyksikko_2#1 sv"
                :fi "opintojenlaajuusyksikko_2#1 fi"}}
              :opintojenLaajuusNumero 53.0
              :kunnat
              [{:koodiUri "kunta_086"
                :nimi {:sv "kunta_086 sv" :fi "kunta_086 fi"}}]
              :tutkintonimikkeet []
              :koulutustyyppi "vapaa-sivistystyo-opistovuosi"}
   :paatellytAlkamiskaudet []})

(def toteutus-search-terms-result
  {:sijainti ["kunta_086" "maakunta_1"]
   :koulutustyypit ["vapaa-sivistystyo" "vapaa-sivistystyo-opistovuosi"]
   :lukiopainotukset []
   :koulutus_organisaationimi {:fi "Opisto oppilaitos"
                               :sv "Opisto oppilaitos"
                               :en "Opisto oppilaitos"}
   :opetuskielet ["oppilaitoksenopetuskieli_1"]
   :lukiolinjaterityinenkoulutustehtava []
   :osaamisalat []
   :koulutusOid "1.2.246.562.13.00000000000000002123"
   :toteutusOid "1.2.246.562.17.00000000000000010979"
   :asiasanat {:fi ["oppivelvollinen" "opistovuosi oppivelvollisille"]}
   :nimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"}
   :koulutusalat ["kansallinenkoulutusluokitus2016koulutusalataso2_001" "ylataso1uri" "ylataso2uri"]
   :kuva "https://konfo-files.opintopolku.fi/toteutus-teemakuva/1.2.246.562.17.00000000000000010979/ed43e1d1-7aa5-4b2b-a4b5-6d20581eeffb.jpg"
   :opetustavat ["opetuspaikkakk_1"]
   :onkoTuleva false
   :oppilaitosOid "1.2.246.562.10.31756159625"
   :toteutus_organisaationimi {:fi ["Opisto oppilaitos" "Opisto toimipiste"]
                               :sv ["Opisto oppilaitos" "Opisto toimipiste"]
                               :en ["Opisto oppilaitos" "Opisto toimipiste"]}
   :isTyovoimakoulutus false
   :hasJotpaRahoitus false
   :isTaydennyskoulutus false
   :isPieniOsaamiskokonaisuus true
   :koulutusnimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"
                  :sv "Folkhögskoleåret för läropliktiga"}
   :koulutusKuvaus {:fi "Kuvaus fi"
                    :sv "Kuvaus sv"}
   :toteutusKuvaus {:fi "Kuvaus"}
   :jarjestaaUrheilijanAmmKoulutusta false
   :metadata {:tutkintonimikkeet []
              :opetusajat [{:koodiUri "opetusaikakk_1#1"
                            :nimi {:fi "opetusaikakk_1#1 fi"
                                   :sv "opetusaikakk_1#1 sv"}}]
              :maksullisuustyyppi "maksuton"
              :koulutustyyppi "vapaa-sivistystyo-opistovuosi"
              :kunnat [{:koodiUri "kunta_086"
                        :nimi {:fi "kunta_086 fi" :sv "kunta_086 sv"}}]
              :onkoApuraha false}
   :toteutusNimi {:fi "Opistovuosi oppivelvollisille kansanopistoissa"}
   :paatellytAlkamiskaudet ["2023-syksy"]})

(deftest koulutus-search-terms
  (with-redefs [kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache mock-list-alakoodi-nimet
                kouta-indeksoija-service.rest.koodisto/get-ylakoodit-with-cache mock-get-ylakoodisto
                kouta-indeksoija-service.rest.koodisto/get-koodi-nimi-with-cache mock-get-koodi-nimi]
    (testing "returns empty map if empty list given as a parameter"
      (is (= koulutus-search-terms-result
             (oppilaitos-search/koulutus-search-terms oppilaitos koulutus))))))

(deftest toteutus-search-terms
  (with-redefs [kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache mock-list-alakoodi-nimet
                kouta-indeksoija-service.rest.koodisto/get-ylakoodit-with-cache mock-get-ylakoodisto
                kouta-indeksoija-service.rest.koodisto/get-koodi-nimi-with-cache mock-get-koodi-nimi]
    (testing "returns empty map if empty list given as a parameter"
      (is (= (merge toteutus-search-terms-result {:metadata (merge (:metadata toteutus-search-terms-result) {:suunniteltuKestoKuukausina 12})})
             (oppilaitos-search/toteutus-search-terms oppilaitos koulutus [] toteutus))))))
