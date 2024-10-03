(ns kouta-indeksoija-service.indexer.tools.koodisto
  (:require [kouta-indeksoija-service.rest.koodisto :refer :all]
            [clojure.string]
            [kouta-indeksoija-service.util.time :refer [date-is-before-now?]]))

(defonce koodiuri-yhteishaku-hakutapa "hakutapa_01")

(defonce koodiuri-opintopiste-laajuusyksikko "opintojenlaajuusyksikko_2#1")

(defonce koodiuri-osaamispiste-laajuusyksikko "opintojenlaajuusyksikko_6#1")

(defonce koodiuri-viikko-laajuusyksikko "opintojenlaajuusyksikko_8#1")

(defonce koodiuri-kurssi-laajuusyksikko "opintojenlaajuusyksikko_4")

(defonce koodiuri-ylioppilas-tutkintonimike "tutkintonimikkeet_00001#1")

(defonce koodiuri-koulutusalataso1 "kansallinenkoulutusluokitus2016koulutusalataso1")

(defonce koodiuri-koulutusalataso2 "kansallinenkoulutusluokitus2016koulutusalataso2")

(defonce koulutusalataso-koodistot [koodiuri-koulutusalataso1
                                    koodiuri-koulutusalataso2
                                    "kansallinenkoulutusluokitus2016koulutusalataso3"
                                    "okmohjauksenala"])

(defonce koulutusaste-koodistot ["kansallinenkoulutusluokitus2016koulutusastetaso1"
                                 "kansallinenkoulutusluokitus2016koulutusastetaso2"])

(defn maakunta
  [kuntaKoodiUri]
  (get-alakoodi-nimi-with-cache kuntaKoodiUri "maakunta"))

(defn koulutusalat-taso1
  [koulutusKoodiUri]
  (list-alakoodi-nimet-with-cache koulutusKoodiUri koodiuri-koulutusalataso1))

(defn koulutusalat-taso2
  [koulutusKoodiUri]
  (list-alakoodi-nimet-with-cache koulutusKoodiUri koodiuri-koulutusalataso2))

(defn- get-koodiurit-from-ala-koodistot
  [koulutusKoodiUri koodistot]
  (->> koodistot
       (mapcat #(list-alakoodi-nimet-with-cache koulutusKoodiUri %))
       (map :koodiUri)))

(defn- get-koodiurit-from-yla-koodistot
  [koulutusKoodiUri koodistot]
  (->> koodistot
       (mapcat #(list-ylakoodit-with-cache koulutusKoodiUri %))
       (map :koodiUri)))

(defn koulutusalat
  [koulutusKoodiUri]
  (get-koodiurit-from-ala-koodistot koulutusKoodiUri koulutusalataso-koodistot))

(defn koulutusalan-ylakoulutusalat
  [koulutusalaKoodiUri]
  (get-koodiurit-from-yla-koodistot koulutusalaKoodiUri [koodiuri-koulutusalataso1 koodiuri-koulutusalataso2]))

(defn koulutusasteet
  [koulutusKoodiUri]
  (get-koodiurit-from-ala-koodistot koulutusKoodiUri koulutusaste-koodistot))

(defn koulutustyypit
  [koulutusKoodiUri]
  (list-alakoodi-nimet-with-cache koulutusKoodiUri "koulutustyyppi"))

(defn tutkintotyypit
  [koulutusKoodiUri]
  (list-alakoodi-nimet-with-cache koulutusKoodiUri "tutkintotyyppi"))

;eiharkinnanvaraisuutta koodin kuvaus: "Koulutukselta ei kysytä harkinnanvaraisuutta (ei siis suodatu hakukohderyhmäpalvelun harkinnanvarainen valinta-listaukseen), jos sillä on pääsykoe."
(defn harkinnanvaraisuutta-ei-kysyta-lomakkeella
  [koulutusKoodiUri]
  (let [asetukset (list-alakoodi-nimet-with-cache koulutusKoodiUri "hakulomakkeenasetukset")]
    (some #(= "hakulomakkeenasetukset_eiharkinnanvaraisuutta" (get % :koodiUri)) asetukset)))

(defn pohjakoulutusvaatimuskonfo
  []
  (map #(assoc % :alakoodit (list-alakoodit-with-cache (:koodiUri %) "pohjakoulutusvaatimuskouta"))
       (get-koodit-with-cache "pohjakoulutusvaatimuskonfo")))

(defn painotettavatoppiaineetlukiossa-koodiurit
  []
  (map #(str (get % :koodiUri) "#" (get % :versio))
       (get-koodit-with-cache "painotettavatoppiaineetlukiossa")))

(defn assoc-hakukohde-nimi-from-koodi [hakukohde]
  (let [hakukohde-koodi-uri (:hakukohdeKoodiUri hakukohde)
        hakukohde-koodi-nimi (when-not (clojure.string/blank? hakukohde-koodi-uri)
                               (get-koodi-nimi-with-cache hakukohde-koodi-uri))]
    (if (nil? hakukohde-koodi-nimi) hakukohde (assoc hakukohde :nimi (:nimi hakukohde-koodi-nimi)))))

(defn filter-expired [koodit]
  (filter (fn [koodi]
            (not (when-let [loppu (:voimassaLoppuPvm koodi)]
                   (date-is-before-now? loppu)))) koodit))
