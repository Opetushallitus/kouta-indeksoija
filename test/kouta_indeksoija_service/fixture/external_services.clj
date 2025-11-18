(ns kouta-indeksoija-service.fixture.external-services
  (:require [clojure.string :as string]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]))

(def koodi-uri-lock (Object.))

(defn mock-koodisto
  ([_ koodi-uri]
   (locking koodi-uri-lock
     (if koodi-uri
       {:koodiUri koodi-uri
        :nimi {:fi (str koodi-uri " nimi fi") :sv (str koodi-uri " nimi sv")}})))
  ([koodi-uri]
   (locking koodi-uri-lock
     (if koodi-uri
       (mock-koodisto (subs koodi-uri 0 (string/index-of koodi-uri "_")) koodi-uri)))))

(defn mock-koodi-nimi-and-arvo-with-cache
  [_ koodi-uri]
  (if koodi-uri
    {:koodiUri koodi-uri :koodiArvo (re-find #"\d{5}" koodi-uri) :nimi {:fi (str koodi-uri " nimi fi") :sv (str koodi-uri " nimi sv")}}
    nil))

(defn mock-alakoodit
  [koodi-uri alakoodisto-uri]
  (cond
    (= "koulutus_222336#1" koodi-uri)
    (vector {:koodiUri "koulutustyyppiabc_01" :nimi {:fi (str koodi-uri "_01" " nimi fi") :sv (str koodi-uri "_01" " nimi sv")}})
    (= "koulutus_222337#1" koodi-uri)
    (vector {:koodiUri "koulutustyyppiabc_01" :nimi {:fi (str koodi-uri "_01" " nimi fi") :sv (str koodi-uri "_01" " nimi sv")} :tila "PASSIIVINEN"})
    :else (vector
           {:koodiUri (str alakoodisto-uri "_01") :nimi {:fi (str alakoodisto-uri "_01" " nimi fi") :sv (str alakoodisto-uri "_01" " nimi sv")}}
           {:koodiUri (str alakoodisto-uri "_02") :nimi {:fi (str alakoodisto-uri "_02" " nimi fi") :sv (str alakoodisto-uri "_02" " nimi sv")}})))

(defn mock-ylakoodit
  [koodi-uri alakoodisto-uri]
  nil)

(defn mock-get-henkilo-nimi-with-cache
  [oid]
  (locking oid "Kalle Ankka"))

(defn mock-get-eperuste
  [id]
  {:id id
   :diaarinumero "1111-OPH-2021"
   :tyotehtavatJoissaVoiToimia {:_id "2563"
                                :fi "Tyotehtavat joissa voi toimia (eperuste) fi"
                                :sv "Tyotehtavat joissa voi toimia (eperuste) sv"}
   :suorittaneenOsaaminen {:_id "4938"
                           :fi "Suorittaneen osaaminen (eperuste) fi"
                           :sv "Suorittaneen osaaminen (eperuste) sv"}
   :voimassaoloLoppuu 1514757600000
   :koulutukset [{:nimi {:fi (str "koulutus " id " nimi fi")
                         :sv (str "koulutus " id " nimi sv")}
                  :koulutuskoodiUri "koulutus_354345"}]
   :tutkintonimikkeet [{:tutkintonimikeUri "tutkintonimikkeet_01" :nimi {:fi "tutkintonimikkeet_01 nimi fi" :sv "tutkintonimikkeet_01 nimi sv"}}
                       {:tutkintonimikeUri "tutkintonimikkeet_02" :nimi {:fi "tutkintonimikkeet_02 nimi fi" :sv "tutkintonimikkeet_02 nimi sv"}}]
   :suoritustavat [{:laajuusYksikko "OSAAMISPISTE"
                    :rakenne {:muodostumisSaanto {:laajuus {:minimi 150 :maksimi 150}}
                              :osat [{:tunniste "osaamisala tunniste 1"
                                      :osaamisala {:nimi {:fi "Osaamisala 01 fi" :sv "Osaamisala 01 sv"}
                                                   :osaamisalakoodiArvo "01"
                                                   :osaamisalakoodiUri "osaamisala_01"}
                                      :muodostumisSaanto {:laajuus {:minimi 30 :maksimi 30}}}]}
                    :tutkinnonOsaViitteet [{:id 122, :laajuus 50, :jarjestys 1, :_tutkinnonOsa 1234}]}]
   :tutkinnonOsat [{:id 1234 :koodi {:nimi {:fi "tutkinnon osa 123 fi" :sv "tutkinnon osa 123 sv"}
                                     :uri "tutkinnonosat_12345"}}]
   :lops2019 {:oppiaineet [{:id 6835372 :moduulit [{:koodi {:uri "moduulikoodistolops2021_kald3"}
                                                    :tavoitteet {:kohde {:fi "Tavoitteet kohde fi" :sv "Tavoitteet kohde sv" :en "Tavoitteet kohde en"}
                                                                 :tavoitteet [{:fi "Tavoite 1 fi" :sv "Tavoite 1 sv"} {:fi "Tavoite 2 fi" :sv "Tavoite 2 sv"}]}
                                                    :sisallot [{:sisallot [{:fi "Sisalto 1 fi" :sv "Sisalto 1 sv"} {:fi "Sisalto 2 fi" :sv "Sisalto 2 sv"}]}]}]}]}})

(defn mock-get-osaamisalakuvaukset
  [eperuste-id eperuste-tila]
  [{:id 5697410
    :nimi {:fi "Osaamisala"
           :sv "Kompetensområdet"}
    :tila "valmis"
    :teksti {:fi "<p>Kuvaus suomeksi</p>"
             :sv "<p>Kuvaus ruotsiksi</p<"}
    :osaamisala {:nimi {:fi "Osaamisala"
                        :sv "Kompetensområdet"}
                 :uri "osaamisala_3157"
                 :koodisto "osaamisala"}}])

(defn osaamismerkki
  [koodi-uri]
  {:id 9203135
   :nimi {:_id "9204297"
          :_tunniste "5596ec44-7305-44a4-a10e-f2cb2f04182e"
          :fi "Digitaalinen turvallisuus"
          :sv "Digital säkerhet"}
   :kuvaus nil
   :tila :JULKAISTU
   :kategoria {:id 9202623
               :nimi {:_id "9202528"
                      :_tunniste "6d20f392-f411-4e85-9d00-559411a6e4d7"
                      :fi "Digitaidot"
                      :sv "Digital kompetens"}
               :kuvaus nil
               :liite {:id "ff78de54-0090-484f-87ce-802ea6c70156"
                       :nimi "digitaidot_eitekstia.png"
                       :mime "image/png"
                       :binarydata "iVBORw0KGgoAAAANSUhEUgA"}
               :muokattu 1707992127262}
   :koodiUri koodi-uri
   :osaamistavoitteet [{:id 9203213
                        :osaamistavoite {:_id "9204298"
                                         :_tunniste "d3d0a69a-930e-49b7-8355-c3965f998468"
                                         :fi "osaa toimia turvallisesti digitaalisissa toimintaympäristöissä"
                                         :sv "kan handla tryggt i digitala verksamhetsmiljöer"}}]
   :arviointikriteerit [{:id 9203207
                         :arviointikriteeri {:_id "9204292"
                                             :_tunniste "5f3884a4-353a-4d5b-95cf-163ba318f8e5"
                                             :fi "luettelee yleisimpiä tietoturvariskejä"
                                             :sv "räknar upp de vanligaste datasäkerhetsriskerna"}}
                        {:id 9203209
                         :arviointikriteeri {:_id "9204294"
                                             :_tunniste "8c6e7039-913e-4fa6-b309-271a4f96f256"
                                             :fi "nimeää henkilötietojen käsittelyyn liittyviä yksilön oikeuksia"
                                             :sv "ger exempel på individens rättigheter vid behandling av personuppgifter"}}]
   :voimassaoloAlkaa 1704060000000
   :voimassaoloLoppuu nil
   :muokattu 1707992127262
   :muokkaaja "1.2.246.562.24.16945731101"})

(defn mock-get-osaamismerkki
  [koodi-uri]
  (osaamismerkki koodi-uri))

(defn mock-fetch-all-osaamismerkit
  []
  [(osaamismerkki "osaamismerkki_1008")])
