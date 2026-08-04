(ns kouta-indeksoija-service.indexer.kouta.koulutus
  (:require [kouta-indeksoija-service.indexer.cache.eperuste :refer [filter-tutkinnon-osa get-eperuste-by-id]]
            [kouta-indeksoija-service.rest.eperuste :refer [get-paikalliset-tutkinnonosat-with-cache
                                                            get-opetussuunnitelma-with-cache]]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.tools.general :refer [amm-koulutus-with-eperuste? amm-osaamisala? amm-tutkinnon-osa? ammatillinen?
                                                                    get-non-korkeakoulu-koodi-uri lukio? not-poistettu?]]
            [kouta-indeksoija-service.indexer.tools.koodisto :refer [koodiuri-ylioppilas-tutkintonimike koulutusalat koulutusalat-taso1 koulutusasteet]]
            [kouta-indeksoija-service.indexer.tools.koulutustyyppi :refer [assoc-koulutustyyppi-path]]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [remove-uri-version]]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache
                                                            list-alakoodi-nimet-with-cache]]
            [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.util.time :refer [long->indexed-date-time]]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec
                                                         get-oids get-esitysnimi]]))

(def index-name "koulutus-kouta")

(defn- enrich-ammatillinen-metadata
  [koulutus]
  (let [koulutusKoodi (get-non-korkeakoulu-koodi-uri koulutus)
        eperuste (get-eperuste-by-id (:ePerusteId koulutus))]
    (if (amm-koulutus-with-eperuste? koulutus)
      (-> koulutus
          (assoc-in [:metadata :tutkintonimike]          (->distinct-vec (map (fn [x] {:koodiUri (:tutkintonimikeUri x) :nimi (:nimi x)}) (:tutkintonimikkeet eperuste))))
          (assoc-in [:metadata :opintojenLaajuus]        (:opintojenLaajuus eperuste))
          (assoc-in [:metadata :opintojenLaajuusyksikko] (:opintojenLaajuusyksikko eperuste))
          (assoc-in [:metadata :koulutusala]             (koulutusalat-taso1 koulutusKoodi)))
      koulutus)))

(defn- get-enriched-tutkinnon-osat
  [tutkinnon-osat]
  (vec (for [tutkinnon-osa tutkinnon-osat
             :let [eperuste-id (:ePerusteId tutkinnon-osa)
                   eperuste (get-eperuste-by-id eperuste-id)
                   eperuste-tutkinnon-osa (filter-tutkinnon-osa eperuste (:tutkinnonosaId tutkinnon-osa))]]
         (merge tutkinnon-osa
                {:opintojenLaajuusNumero (:opintojenLaajuusNumero eperuste-tutkinnon-osa)
                 :opintojenLaajuus (:opintojenLaajuus eperuste-tutkinnon-osa)
                 :opintojenLaajuusyksikko (:opintojenLaajuusyksikko eperuste)
                 :tutkinnonOsat (select-keys eperuste-tutkinnon-osa [:koodiUri :nimi])}))))

(defn- get-enriched-paikalliset-tutkinnon-osat
  [paikalliset-tutkinnon-osat]
  (when (seq paikalliset-tutkinnon-osat)
    (let [opetussuunnitelma-id  (-> paikalliset-tutkinnon-osat first :opetussuunnitelmaId)
          amosaa-osat           (get-paikalliset-tutkinnonosat-with-cache opetussuunnitelma-id)
          eperuste-id           (some-> (get-opetussuunnitelma-with-cache opetussuunnitelma-id)
                                        (get-in [:peruste :perusteId]))
          laajuusyksikko        (some-> eperuste-id get-eperuste-by-id :opintojenLaajuusyksikko)]
      (vec (for [osa paikalliset-tutkinnon-osat
                 :let [amosaa-osa (first (filter #(= (str (:id %)) (str (:tutkinnonosaId osa)))
                                                 amosaa-osat))]]
             (merge osa {:nimi                    (:nimi amosaa-osa)
                         :opintojenLaajuusNumero  (get-in amosaa-osa [:tosa :omatutkinnonosa :laajuus])
                         :opintojenLaajuusyksikko laajuusyksikko}))))))

(defn- enrich-tutkinnon-osa-metadata
  [koulutus]
  (let [tutkinnon-osat (get-in koulutus [:metadata :tutkinnonOsat])]
    (-> koulutus
        (assoc-in [:metadata :tutkinnonOsat] (get-enriched-tutkinnon-osat tutkinnon-osat))
        (assoc-in [:metadata :paikallisetTutkinnonOsat] (get-enriched-paikalliset-tutkinnon-osat (get-in koulutus [:metadata :paikallisetTutkinnonOsat])))
        (assoc-in [:metadata :koulutusala] (->> tutkinnon-osat
                                                (map #(get-in % [:koulutus :koodiUri]))
                                                (mapcat #(koulutusalat-taso1 %))
                                                (distinct))))))

(defn- get-osaamisala
  [eperuste koulutus]
  (when-let [osaamisalaKoodiUri (some-> koulutus :metadata :osaamisala :koodiUri remove-uri-version)]
    (some->> eperuste
             :osaamisalat
             (filter #(= osaamisalaKoodiUri (some-> % :koodiUri remove-uri-version)))
             (first))))

(defn- enrich-osaamisala-metadata
  [koulutus]
  (let [koulutusKoodi (get-non-korkeakoulu-koodi-uri koulutus)
        eperuste (some-> koulutus :ePerusteId (get-eperuste-by-id))
        osaamisala (get-osaamisala eperuste koulutus)]
    (-> koulutus
        (assoc-in [:metadata :opintojenLaajuus] (:opintojenLaajuus osaamisala))
        (assoc-in [:metadata :opintojenLaajuusyksikko] (:opintojenLaajuusyksikko eperuste))
        (assoc-in [:metadata :opintojenLaajuusNumero] (:opintojenLaajuusNumero osaamisala))
        (assoc-in [:metadata :koulutusala] (koulutusalat-taso1 koulutusKoodi)))))

(defn- enrich-lukio-metadata
  [koulutus]
  (assoc-in koulutus [:metadata :tutkintonimike] (vector (get-koodi-nimi-with-cache koodiuri-ylioppilas-tutkintonimike))))

(defn- does-not-have-tutkintonimike?
  [koulutus]
  (nil? (get-in koulutus [:metadata :tutkintonimike])))

(defn- get-alakoodis-for-multiple-koodiUri
  [koodiUrit alaKoodiKoodistoUri]
  (let [alakoodit (map #(list-alakoodi-nimet-with-cache % alaKoodiKoodistoUri) koodiUrit)]
    (->distinct-vec (flatten alakoodit))))

(defn- assoc-eqf-and-nqf
  [koulutus]
  (let [koulutusKoodiUrit (map #(get % :koodiUri) (get koulutus :koulutukset))
        eqf (get-alakoodis-for-multiple-koodiUri koulutusKoodiUrit "eqf")
        nqf (get-alakoodis-for-multiple-koodiUri koulutusKoodiUrit "nqf")]
    (-> koulutus
        (assoc :eqf eqf)
        (assoc :nqf nqf))))

(defn- enrich-common-metadata
  [koulutus]
  (let [eperuste (some-> koulutus :ePerusteId (get-eperuste-by-id))]
    (cond-> koulutus
      (does-not-have-tutkintonimike? koulutus) (assoc-in [:metadata :tutkintonimike] [])
      (some? eperuste) (#(-> %
                             (assoc-in [:metadata :eperuste :id]                (:id eperuste))
                             (assoc-in [:metadata :eperuste :diaarinumero]      (:diaarinumero eperuste))
                             (assoc-in [:metadata :eperuste :voimassaoloLoppuu] (some-> eperuste
                                                                                        :voimassaoloLoppuu
                                                                                        (long->indexed-date-time))))))))

(defn- enrich-koulutustyyppi-based-metadata
  [koulutus]
  (cond
    (ammatillinen? koulutus)          (enrich-ammatillinen-metadata koulutus)
    (amm-tutkinnon-osa? koulutus)     (enrich-tutkinnon-osa-metadata koulutus)
    (amm-osaamisala? koulutus)        (enrich-osaamisala-metadata koulutus)
    (lukio? koulutus)                 (enrich-lukio-metadata koulutus)
    :else koulutus))

(defn- enrich-metadata
  [koulutus]
  (-> koulutus
      (enrich-common-metadata)
      (enrich-koulutustyyppi-based-metadata)))

(defn- assoc-sorakuvaus
  [koulutus execution-id]
  (if-let [sorakuvaus-id (:sorakuvausId koulutus)]
    (assoc koulutus :sorakuvaus (common/complete-entry (kouta-backend/get-sorakuvaus-with-cache sorakuvaus-id execution-id)))
    koulutus))

(defn- create-koulutuskoodiuri-with-aste-and-ala
  [koodiuri]
  {:koulutusKoodiUri koodiuri
   :koulutusalaKoodiUrit (koulutusalat koodiuri)
   :koulutusasteKoodiUrit (koulutusasteet koodiuri)})

;odw requires :koulutuskoodienAlatJaAsteet information (OY-3710)
(defn- assoc-koulutusala-and-koulutusaste
  [koulutus]
  (let [koulutusAlaJaAstekoodiUrit (->> (:koulutukset koulutus)
                                        (map :koodiUri)
                                        (map create-koulutuskoodiuri-with-aste-and-ala))]
    (assoc koulutus :koulutuskoodienAlatJaAsteet koulutusAlaJaAstekoodiUrit)))


(defn create-index-entry
  [oid execution-id]
  (let [koulutus (kouta-backend/get-koulutus-with-cache oid execution-id)
        koulutus-intermediate (common/complete-entry koulutus)]
    (if (not-poistettu? koulutus)
      (let [toteutukset (common/complete-entries (kouta-backend/get-toteutus-list-for-koulutus-with-cache oid execution-id))
            hakutiedot (when toteutukset (kouta-backend/get-hakutiedot-for-koulutus-with-cache oid execution-id))
            haku-oids (get-oids :hakuOid (mapcat :haut hakutiedot))
            koulutus-enriched (-> koulutus-intermediate
                                  (assoc-koulutustyyppi-path koulutus)
                                  (common/assoc-organisaatiot)
                                  (enrich-metadata)
                                  (assoc-eqf-and-nqf)
                                  (assoc-sorakuvaus execution-id)
                                  (assoc :haut haku-oids)
                                  (assoc :toteutukset (map common/toteutus->list-item toteutukset))
                                  (assoc :nimi (get-esitysnimi koulutus))
                                  (assoc-koulutusala-and-koulutusaste)
                                  (common/localize-dates))]
        (indexable/->index-entry-with-forwarded-data oid koulutus-enriched koulutus-intermediate))
      (indexable/->delete-entry-with-forwarded-data oid koulutus-intermediate))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
