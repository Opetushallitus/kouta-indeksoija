(ns kouta-indeksoija-service.indexer.kouta.toteutus
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.cache.eperuste :refer [lukio-eperuste-id get-eperuste-by-id]]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [remove-uri-version]]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec get-esitysnimi get-oids]]
            [kouta-indeksoija-service.indexer.tools.koulutustyyppi :refer [assoc-koulutustyyppi-path]]
            [kouta-indeksoija-service.indexer.tools.general :refer [not-poistettu?]]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :refer [complete-painotetut-lukioarvosanat-kaikki]]))

(def index-name "toteutus-kouta")

(defn lukiototeutus? [toteutus] (= "lk" (get-in toteutus [:metadata :tyyppi])))

(defn- determine-correct-aikataulu-and-hakulomake
  [ht-haku ht-hakukohde]
  (let [hakulomakeKeys  [:hakulomaketyyppi :hakulomakeAtaruId :hakulomakeKuvaus :hakulomakeLinkki]
        alkamisaikaKey  [:koulutuksenAlkamiskausi]
        aikatauluKeys   [:hakuajat]
        hakukohdeOid         (:hakukohdeOid ht-hakukohde)]
    (merge {}
           (if (true? (:kaytetaanHaunHakulomaketta ht-hakukohde))
             (conj (select-keys ht-haku hakulomakeKeys) (common/create-hakulomake-linkki-for-hakukohde ht-haku hakukohdeOid))
             (conj (select-keys ht-hakukohde hakulomakeKeys) (common/create-hakulomake-linkki-for-hakukohde ht-hakukohde hakukohdeOid)))
           (if (true? (:kaytetaanHaunAlkamiskautta ht-hakukohde))
             (select-keys ht-haku alkamisaikaKey)
             (select-keys ht-hakukohde alkamisaikaKey))
           (if (true? (:kaytetaanHaunAikataulua ht-hakukohde))
             (select-keys ht-haku aikatauluKeys)
             (select-keys ht-hakukohde aikatauluKeys)))))

(defn- assoc-has-valintaperustekuvaus-data [ht-hakukohde]
  (let [kynnysehto (:kynnysehto ht-hakukohde)
        valintakoe-ids (:valintakoeIds ht-hakukohde)]
    (assoc ht-hakukohde :hasValintaperustekuvausData (boolean (or (seq kynnysehto) (seq valintakoe-ids))))))

(defn- complete-painotetut-lukioarvosanat-if-exists
  [hakukohde]
  (if-let [painotetut-arvosanat (get-in hakukohde [:hakukohteenLinja :painotetutArvosanat])]
    (assoc-in hakukohde [:hakukohteenLinja :painotetutArvosanatOppiaineittain]
              (complete-painotetut-lukioarvosanat-kaikki painotetut-arvosanat))
    hakukohde))

(defn- create-hakukohteiden-hakutiedot
  [ht-haku]
  (for [ht-hakukohde (:hakukohteet ht-haku)]
    (-> ht-hakukohde
        (assoc-has-valintaperustekuvaus-data)
        (select-keys [:hakukohdeOid
                      :nimi
                      :modified
                      :tila
                      :esikatselu
                      :valintaperusteId
                      :pohjakoulutusvaatimusKoodiUrit
                      :pohjakoulutusvaatimusTarkenne
                      :aloituspaikat
                      :hakukohteenLinja
                      :jarjestyspaikkaOid
                      :organisaatioOid
                      :hasValintaperustekuvausData
                      :jarjestaaUrheilijanAmmKoulutusta
                      :metadata])
        (complete-painotetut-lukioarvosanat-if-exists)
        (merge (determine-correct-aikataulu-and-hakulomake ht-haku ht-hakukohde))
        (common/decorate-koodi-uris)
        (common/assoc-jarjestyspaikka)
        (common/assoc-organisaatio))))

(defn- determine-correct-hakutiedot
  [ht-toteutus]
  (-> (for [ht-haku (:haut ht-toteutus)]
        (-> (select-keys ht-haku [:hakuOid :nimi :hakutapaKoodiUri :koulutuksenAlkamiskausi :kohdejoukkoKoodiUri])
            (common/decorate-koodi-uris)
            (assoc :hakukohteet (vec (create-hakukohteiden-hakutiedot ht-haku)))))
      (vec)))

(defn- assoc-hakutiedot
  [toteutus hakutiedot]
  (if-let [ht-toteutus (first (filter (fn [x] (= (:toteutusOid x) (:oid toteutus))) hakutiedot))]
    (assoc toteutus :hakutiedot (determine-correct-hakutiedot ht-toteutus))
    toteutus))

(defn- assoc-tarjoajien-oppilaitokset
  [toteutus]
  (assoc toteutus :oppilaitokset
         (->> (:tarjoajat toteutus)
              (map :oid)
              (map #(cache/find-oppilaitos-by-own-or-child-oid %))
              (remove nil?)
              (filter organisaatio-tool/indexable?)
              (map :oid)
              (->distinct-vec))))


(defn assoc-liitetyt
  ([toteutus liitetyt key-for-liitetyt]
   (assoc-liitetyt toteutus liitetyt key-for-liitetyt false))

  ([toteutus liitetyt key-for-liitetyt with-metadata]
   (assoc toteutus
          key-for-liitetyt
          (for [liitetty liitetyt]
            (let [base {:nimi (:nimi liitetty)
                        :oid (:oid liitetty)}]
              (if with-metadata
                (merge base (common/decorate-koodi-uris
                              {:metadata {:kuvaus (get-in liitetty [:metadata :kuvaus])
                                          :opintojenLaajuusNumero (get-in liitetty [:metadata :opintojenLaajuusNumero])
                                          :opintojenLaajuusyksikkoKoodiUri (get-in liitetty [:metadata :opintojenLaajuusyksikkoKoodiUri])}}))
                base))))))


;Palauttaa toteutuksen johon on rikastettu lukiodiplomeiden sisällöt ja tavoitteet eperusteista
(defn- enrich-lukiodiplomit
  [toteutus eperuste]
  (let [lukiodiplomi-sisallot-tavoitteet (get-in eperuste [:diplomiSisallotTavoitteet])
        toteutus-diplomit (get-in toteutus [:metadata :diplomit])]
    (assoc-in toteutus [:metadata :diplomit]
              (vec (map (fn [diplomi]
                          (let [koodi-uri (remove-uri-version (get-in diplomi [:koodi :koodiUri]))]
                            (merge diplomi (get-in lukiodiplomi-sisallot-tavoitteet [koodi-uri]))))
                        toteutus-diplomit)))))

(defn- enrich-lukio-metadata
  [toteutus]
  (let [eperuste (get-eperuste-by-id lukio-eperuste-id)]
    (enrich-lukiodiplomit toteutus eperuste)))

(defn- enrich-metadata
  [toteutus]
  (cond
    (lukiototeutus? toteutus) (enrich-lukio-metadata toteutus)
    :else toteutus))

(defn create-index-entry
  [oid execution-id]
  (let [toteutus (kouta-backend/get-toteutus-with-cache oid execution-id)
        koulutus (kouta-backend/get-koulutus-with-cache (:koulutusOid toteutus) execution-id)]
    (if (not-poistettu? toteutus)
      (let [hakutiedot (kouta-backend/get-hakutiedot-for-koulutus-with-cache (:koulutusOid toteutus) execution-id)
            haut       (kouta-backend/list-haut-by-toteutus-with-cache oid execution-id)
            haku-oids  (get-oids :oid haut)
            opintojaksot (when-let [liitetyt-opintojaksot (get-in toteutus [:metadata :liitetytOpintojaksot])]
                           (kouta-backend/get-toteutukset-with-cache
                            liitetyt-opintojaksot
                            execution-id))
            opintokokonaisuudet (when (= "kk-opintojakso" (get-in toteutus [:metadata :tyyppi]))
                                  (kouta-backend/get-opintokokonaisuudet-by-toteutus-oids-with-cache
                                   [(:oid toteutus)]
                                   execution-id))
            osaamismerkit (when-let [liitetyt-osaamismerkit (get-in toteutus [:metadata :liitetytOsaamismerkit])]
                            (kouta-backend/get-koulutukset-with-cache
                             liitetyt-osaamismerkit
                             execution-id))
            toteutus-enriched (-> toteutus
                                  (common/complete-entry)
                                  (common/assoc-organisaatiot)
                                  (assoc :koulutustyyppi (get-in toteutus [:metadata :tyyppi]))
                                  (assoc-koulutustyyppi-path koulutus (:metadata toteutus))
                                  (assoc :nimi (get-esitysnimi toteutus))
                                  (assoc :haut haku-oids)
                                  (enrich-metadata)
                                  (assoc-tarjoajien-oppilaitokset)
                                  (assoc-hakutiedot hakutiedot)
                                  (assoc-liitetyt opintojaksot :liitetytOpintojaksot true)
                                  (assoc-liitetyt osaamismerkit :liitetytOsaamismerkit)
                                  (assoc :kuuluuOpintokokonaisuuksiin opintokokonaisuudet)
                                  (common/localize-dates)
                                  (common/remove-empty-p-tags))]
        (indexable/->index-entry-with-forwarded-data oid toteutus-enriched toteutus-enriched))
      (indexable/->delete-entry-with-forwarded-data oid toteutus))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
