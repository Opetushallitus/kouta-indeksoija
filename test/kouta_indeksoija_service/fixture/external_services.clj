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
  [{
    :id 5697410
    :nimi {
           :fi "Osaamisala"
           :sv "Kompetensområdet"}
    :tila "valmis"
    :teksti {
             :fi "<p>Kuvaus suomeksi</p>"
             :sv "<p>Kuvaus ruotsiksi</p<"}
    :osaamisala {
                 :nimi {
                        :fi "Osaamisala"
                        :sv "Kompetensområdet"}
                 :uri "osaamisala_3157"
                 :koodisto "osaamisala"}}])
