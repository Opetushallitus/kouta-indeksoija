(ns kouta-indeksoija-service.indexer.cache.eperuste
  (:require [kouta-indeksoija-service.rest.eperuste :as eperuste-service]
            [kouta-indeksoija-service.indexer.tools.organisaatio :refer :all]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [clojure.core.cache :as cache]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [eperuste-laajuusyksikko->opintojen-laajuusyksikko]]))

(defonce eperuste_cache_time_millis (* 1000 60 30))

(defonce EPERUSTE_CACHE (atom (cache/ttl-cache-factory {} :ttl eperuste_cache_time_millis)))

(defonce lukio-eperuste-id 6828810)

(defonce lukiodiplomit-oppiaine-id 6835372)

(defonce language-keys [:fi :sv :en])

(defn- get-opintojen-laajuus
  [opintojenLaajuusNumero]
  (when opintojenLaajuusNumero
    (let [opintojenLaajuusNumeroAsInt (if (string? opintojenLaajuusNumero) (Integer/parseInt opintojenLaajuusNumero) (int opintojenLaajuusNumero))
          opintojenLaajuusKoodiUri (str "opintojenlaajuus_" opintojenLaajuusNumeroAsInt)
          {:keys [nimi koodiUri]} (get-koodi-nimi-with-cache opintojenLaajuusKoodiUri)]
      {:opintojenLaajuusNumero opintojenLaajuusNumero
       :opintojenLaajuus (if (not (or (nil? nimi) (= {} nimi))) {:nimi nimi :koodiUri koodiUri} {})})))

(defn- get-tutkinnon-osat
  [eperuste]
  (let [tutkinnonOsat (some-> eperuste :tutkinnonOsat)
        tutkinnonOsaViitteet (some-> eperuste :suoritustavat (first) :tutkinnonOsaViitteet)]
    (vec (for [osa tutkinnonOsat
               :let [viite (first (filter #(= (str (:_tutkinnonOsa %)) (str (:id osa))) tutkinnonOsaViitteet))]]
           (merge
             {:id (:id osa)
              :koodiUri (get-in osa [:koodi :uri])
              :nimi (get-in osa [:koodi :nimi])}
             (get-opintojen-laajuus (:laajuus viite)))))))

(defn- filter-osaamisalat
  [osat]
  (filter #(not (nil? (:osaamisala %))) osat))

(defn- get-osaamisalat-recursive
  [osat]
  (concat (filter-osaamisalat osat) (mapcat #(get-osaamisalat-recursive (:osat %)) osat)))

(defn set-default-muodostumissaanto
  [osa default-muodostumissaanto]
  (let [muodostumissaanto (or (:muodostumisSaanto osa) default-muodostumissaanto)]
    (assoc osa
           :muodostumisSaanto muodostumissaanto
           :osat (map #(set-default-muodostumissaanto % muodostumissaanto)
                      (:osat osa)))))

(defn- get-osaamisalat
  [eperuste]
  (let [osat (some-> eperuste :suoritustavat (first) :rakenne :osat)
        osat-with-muodostumissaanto (map #(set-default-muodostumissaanto % nil) osat)]
    (vec (for [osaamisala (get-osaamisalat-recursive osat-with-muodostumissaanto)
               :let [muodostumissaanto (:muodostumisSaanto osaamisala)]]
           (merge
            {:nimi (get-in osaamisala [:osaamisala :nimi])
             :koodiUri (get-in osaamisala [:osaamisala :osaamisalakoodiUri])
             :tunniste (:tunniste osaamisala)}
            (get-opintojen-laajuus (get-in muodostumissaanto [:laajuus :minimi])))))))

(defn- select-language-keys
  [target]
  (select-keys target language-keys))

(defn- get-diplomi-sisallot-tavoitteet
  [eperuste]
  (let [oppiaineet (get-in eperuste [:lops2019 :oppiaineet])]
    (as-> oppiaineet o
      (filter (fn [oppiaine] (= (:id oppiaine) lukiodiplomit-oppiaine-id)) o)
      (first o)
      (get o :moduulit)
      (map (fn [moduuli] [(get-in moduuli [:koodi :uri]) {:sisallot (map select-language-keys (get-in moduuli [:sisallot 0 :sisallot]))
                                                          :tavoitteetKohde (select-language-keys (get-in moduuli [:tavoitteet :kohde]))
                                                          :tavoitteet (map select-language-keys (get-in moduuli [:tavoitteet :tavoitteet]))}]) o)
      (cond (empty? o) nil :else (into {} o)))))

(defn- strip
  [eperuste]
  (let [common-props (select-keys eperuste [:id :diaarinumero :voimassaoloLoppuu :tutkintonimikkeet :koulutukset])]
    (if-let [suoritustapa (some-> eperuste :suoritustavat (first))]
      (let [opintojen-laajuus                 (get-opintojen-laajuus (get-in suoritustapa [:rakenne :muodostumisSaanto :laajuus :minimi]))
            opintojen-laajuusyksikko-koodiuri (eperuste-laajuusyksikko->opintojen-laajuusyksikko (:laajuusYksikko suoritustapa))
            opintojen-laajuusyksikko          (when opintojen-laajuusyksikko-koodiuri (get-koodi-nimi-with-cache opintojen-laajuusyksikko-koodiuri))
            tutkonnon-osat                    (get-tutkinnon-osat eperuste)
            osaamisalat                       (get-osaamisalat eperuste)
            diplomi-sisallot-tavoitteet       (get-diplomi-sisallot-tavoitteet eperuste)]
        (cond-> common-props
          (not (nil? opintojen-laajuus))                (merge opintojen-laajuus)
          (not (nil? opintojen-laajuusyksikko))         (assoc :opintojenLaajuusyksikko opintojen-laajuusyksikko)
          (not (nil? tutkonnon-osat))                   (assoc :tutkinnonOsat tutkonnon-osat)
          (not (nil? osaamisalat))                      (assoc :osaamisalat osaamisalat)
          (not (nil? diplomi-sisallot-tavoitteet))      (assoc :diplomiSisallotTavoitteet diplomi-sisallot-tavoitteet)))
      common-props)))

(defn cache-eperuste-by-id
  [id]
  (when-let [eperuste (eperuste-service/get-doc id)]
    (let [stripped (strip eperuste)]
      (swap! EPERUSTE_CACHE cache/through-cache id (constantly stripped)))))

(defn get-eperuste-by-id
  [id]
  (if-let [eperuste (cache/lookup @EPERUSTE_CACHE id)]
    eperuste
    (do (cache-eperuste-by-id id)
        (cache/lookup @EPERUSTE_CACHE id))))

(defn filter-tutkinnon-osa
  [eperuste tutkinnon-osa-id]
  (some->> eperuste
           :tutkinnonOsat
           (filter #(= (:id %) tutkinnon-osa-id))
           (first)))

(defn get-enriched-paikalliset-tutkinnon-osat
  [paikalliset-tutkinnon-osat]
  (when (seq paikalliset-tutkinnon-osat)
    (vec (for [osa paikalliset-tutkinnon-osat
               :let [opetussuunnitelma-id (:opetussuunnitelmaId osa)
                     amosaa-osa (eperuste-service/get-paikallinen-tutkinnon-osa-with-cache opetussuunnitelma-id (:tutkinnonosaId osa))
                     eperuste-id (some-> (eperuste-service/get-opetussuunnitelma-with-cache opetussuunnitelma-id) (get-in [:peruste :perusteId]))
                     laajuusyksikko (some-> eperuste-id get-eperuste-by-id :opintojenLaajuusyksikko)]]
           (merge osa {:nimi                    (:nimi amosaa-osa)
                       :opintojenLaajuusNumero  (get-in amosaa-osa [:tosa :omatutkinnonosa :laajuus])
                       :opintojenLaajuusyksikko laajuusyksikko})))))