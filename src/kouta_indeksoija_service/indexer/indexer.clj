(ns kouta-indeksoija-service.indexer.indexer
  (:require [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.valintaperuste :as valintaperuste]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]
            [kouta-indeksoija-service.indexer.kouta.sorakuvaus :as sorakuvaus]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :as eperuste]
            [kouta-indeksoija-service.indexer.eperuste.tutkinnonosa :as tutkinnonosa]
            [kouta-indeksoija-service.indexer.eperuste.osaamisalakuvaus :as osaamisalakuvaus]
            [kouta-indeksoija-service.indexer.eperuste.osaamismerkki :as osaamismerkki]
            [kouta-indeksoija-service.indexer.koodisto.koodisto :as koodisto]
            [kouta-indeksoija-service.util.time :refer [long->rfc1123]]
            [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.rest.osaamismerkki :as osaamismerkki-client]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as hierarkia]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.util.tools :refer [get-oids]]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.indexer.lokalisointi.lokalisointi :as lokalisointi]
            [kouta-indeksoija-service.indexer.tools.general :refer [not-poistettu?]]))

(defn- get-ids
  [key coll]
  (set (remove nil? (map key coll))))

(defn eperuste-ids-on-koulutus
  [koulutus]
  (let [osat (get-in koulutus [:metadata :tutkinnonOsat])]
    (concat [(:ePerusteId koulutus)] (map :ePerusteId osat))))

(defn eperuste-ids-on-koulutukset
  [entries]
  (set (remove nil? (mapcat eperuste-ids-on-koulutus entries))))

(defn tutkinnonosa-ids-on-koulutus
  [koulutus]
  (let [osat (get-in koulutus [:metadata :tutkinnonOsat])]
    (map :tutkinnonosatId osat)))

(defn tutkinnonosa-ids-on-koulutukset
  [entries]
  (set (remove nil? (mapcat tutkinnonosa-ids-on-koulutus entries))))

(defn quick-index-koulutukset [oids execution-id]
  (log/info (str "Pikaindeksoidaan koulutukset " oids ", execution id " execution-id))
  (koulutus/do-index oids execution-id))

;;Tässä ja toteutuksen indeksoinnissa pitäisi indeksoida myös hakukohteet sillä
;;hakukohteen indeksoinnissa luetaan koulutuksen ja toteutuksen tietoja ja jos nuo muuttuu
;;kouta-backendissä, ei muutos valu tällä hetkellä indeksoidulle hakukohteelle.
;;Tästä tiketti KTO-1226
(defn index-koulutukset
  [oids execution-id]
  (let [entries (koulutus/do-index oids execution-id)
        not-poistetut (filter not-poistettu? entries)
        tarjoaja-oids (get-oids :oid (mapcat :tarjoajat entries))
        oppilaitos-oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index
                                  (hierarkia/get-hierarkia-cached)
                                  tarjoaja-oids)]
    (koulutus-search/do-index oids execution-id)
    (eperuste/do-index (eperuste-ids-on-koulutukset not-poistetut) execution-id)
    (tutkinnonosa/do-index (tutkinnonosa-ids-on-koulutukset not-poistetut) execution-id)
    (oppilaitos/do-index oppilaitos-oids-to-index execution-id)
    (oppilaitos-search/do-index oppilaitos-oids-to-index execution-id)
    entries))

(defn index-koulutus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (index-koulutukset [oid] execution-id)))

(defn quick-index-koulutus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-koulutukset [oid] execution-id)))

(defn quick-index-toteutukset [oids execution-id]
  (log/info (str "Pikaindeksoidaan toteutukset " oids ", execution id " execution-id))
  (toteutus/do-index oids execution-id))

(defn index-toteutukset
  [oids execution-id]
  (let [toteutus-entries (toteutus/do-index oids execution-id)
        opintokokonaisuus-oids (map
                                #(get % :oid)
                                (kouta-backend/get-opintokokonaisuudet-by-toteutus-oids-with-cache oids execution-id))
        koulutus-oids (get-oids :koulutusOid toteutus-entries)
        haut    (mapcat #(kouta-backend/list-haut-by-toteutus-with-cache % execution-id) oids)
        koulutus-entries (filter not-poistettu? (koulutus/do-index koulutus-oids execution-id))
        tarjoaja-organisaatiot (get-oids :oid (mapcat :tarjoajat toteutus-entries))
        oppilaitos-oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index
                                  (hierarkia/get-hierarkia-cached)
                                  tarjoaja-organisaatiot)]
    (when (seq opintokokonaisuus-oids)
      (toteutus/do-index opintokokonaisuus-oids execution-id))
    (koulutus-search/do-index koulutus-oids execution-id)
    (haku/do-index (get-oids :oid haut) execution-id)
    (osaamisalakuvaus/do-index (eperuste-ids-on-koulutukset koulutus-entries) execution-id)
    (oppilaitos/do-index oppilaitos-oids-to-index execution-id)
    (oppilaitos-search/do-index oppilaitos-oids-to-index execution-id)
    toteutus-entries))

(defn index-toteutus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (index-toteutukset [oid] execution-id)))

(defn quick-index-toteutus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-toteutukset [oid] execution-id)))

(defn quick-index-haut
  [oids execution-id]
  (log/info (str "Pikaindeksoidaan haut " oids ", execution id " execution-id))
  (haku/do-index oids execution-id))

(defn index-haut
  [oids execution-id]
  (let [entries           (haku/do-index oids execution-id)
        hakukohde-entries (hakukohde/do-index (get-oids :oid (mapcat :hakukohteet entries)) execution-id)
        toteutus-entries  (toteutus/do-index (get-oids :toteutusOid hakukohde-entries) execution-id)]
    (koulutus-search/do-index (get-oids :koulutusOid toteutus-entries) execution-id)
    (oppilaitos-search/do-index (get-oids :oid (map :jarjestyspaikka hakukohde-entries)) execution-id)
    entries))

(defn index-haku
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (index-haut [oid] execution-id)))

(defn quick-index-haku
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-haut [oid] execution-id)))

(defn quick-index-hakukohteet [oids execution-id]
  (log/info (str "Pikaindeksoidaan hakukohteet " oids ", execution id " execution-id))
  (hakukohde/do-index oids execution-id))

(defn index-hakukohteet
  [oids execution-id]
  (let [hakukohde-entries (hakukohde/do-index oids execution-id)
        haku-oids         (get-oids :hakuOid hakukohde-entries)
        toteutus-entries  (toteutus/do-index (get-oids :toteutusOid hakukohde-entries) execution-id)]
    (haku/do-index haku-oids execution-id)
    (koulutus-search/do-index (get-oids :koulutusOid toteutus-entries) execution-id)
    (oppilaitos-search/do-index (get-oids :oid (map :jarjestyspaikka hakukohde-entries)) execution-id)
    hakukohde-entries))

(defn index-hakukohde
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
   (index-hakukohteet [oid] execution-id)))

(defn quick-index-hakukohde
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-hakukohteet [oid] execution-id)))

(defn quick-index-valintaperusteet [oids execution-id]
  (log/info (str "Pikaindeksoidaan valintaperusteet " oids ", execution id " execution-id))
  (valintaperuste/do-index oids execution-id))

(defn index-valintaperusteet
  [oids execution-id]
  (let [entries     (valintaperuste/do-index oids execution-id)
        hakukohteet (mapcat #(kouta-backend/list-hakukohteet-by-valintaperuste-with-cache % execution-id) (get-oids :id entries))]
    (hakukohde/do-index (get-oids :oid hakukohteet) execution-id)
    entries))

(defn index-valintaperuste
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (index-valintaperusteet [oid] execution-id)))

(defn quick-index-valintaperuste
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-valintaperusteet [oid] execution-id)))

(defn quick-index-sorakuvaukset [ids execution-id]
  (log/info (str "Pikaindeksoidaan sorakuvaukset " ids ", execution id " execution-id))
  (sorakuvaus/do-index ids execution-id))

(defn index-sorakuvaukset
  [ids execution-id]
  (let [entries       (sorakuvaus/do-index ids execution-id)
        koulutus-oids (mapcat #(kouta-backend/list-koulutus-oids-by-sorakuvaus-with-cache % execution-id) (get-oids :id entries))]
    (koulutus/do-index koulutus-oids execution-id)
    entries))

(defn index-sorakuvaus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
   (index-sorakuvaukset [oid] execution-id)))

(defn quick-index-sorakuvaus
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
    (quick-index-sorakuvaukset [oid] execution-id)))

(defn index-eperusteet
  [oids execution-id]
  (osaamisalakuvaus/do-index oids execution-id)
  (eperuste/do-index oids execution-id))

(defn index-eperuste
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
   (index-eperusteet [oid] execution-id)))

(defn index-osaamismerkit
  [oids execution-id]
  (osaamismerkki/do-index oids execution-id))

(defn index-osaamismerkki
  [koodiUri]
  (let [execution-id (. System (currentTimeMillis))]
    (index-osaamismerkit [koodiUri] execution-id)))

(defn index-oppilaitokset
  ([oids execution-id]
   (index-oppilaitokset oids execution-id true))
  ([oids execution-id clear-cache-before]
   (let [oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index (hierarkia/get-hierarkia-cached) oids)
         hakukohde-oids (kouta-backend/get-hakukohde-oids-by-jarjestyspaikat-with-cache oids-to-index execution-id)
         toteutus-oids (kouta-backend/get-toteutus-oids-by-tarjoajat-with-cache oids-to-index execution-id)
         koulutus-oids (kouta-backend/get-koulutus-oids-by-tarjoajat-with-cache oids-to-index)]
     (oppilaitos/do-index oids-to-index execution-id clear-cache-before)
     (when (not-empty hakukohde-oids) (hakukohde/do-index hakukohde-oids execution-id))
     (oppilaitos-search/do-index oids-to-index execution-id clear-cache-before)
     (koulutus-search/do-index koulutus-oids execution-id)
     (toteutus/do-index toteutus-oids execution-id))))

(defn index-oppilaitos
  [oid]
  (let [execution-id (. System (currentTimeMillis))]
   (index-oppilaitokset [oid] execution-id)))

(defn index-koodistot
  [koodistot]
  (let [execution-id (. System (currentTimeMillis))]
    (koodisto/do-index koodistot execution-id)))

(defn index-lokalisoinnit
  [lngs execution-id]
  (lokalisointi/do-index lngs execution-id))

(defn index-lokalisointi
  [lng]
  (let [execution-id (. System (currentTimeMillis))]
    (index-lokalisoinnit [lng] execution-id)))

(defn index-oids
  [oids execution-id]
  (let [start (. System (currentTimeMillis))]
    (log/info "ID:" execution-id " Indeksoidaan: "
              (count (:koulutukset oids)) "koulutusta, "
              (count (:toteutukset oids)) "toteutusta, "
              (count (:haut oids)) "hakua, "
              (count (:hakukohteet oids)) "hakukohdetta, "
              (count (:valintaperusteet oids)) "valintaperustetta, "
              (count (:sorakuvaukset oids)) "sora-kuvausta, "
              (count (:eperusteet oids)) "eperustetta osaamisaloineen,"
              (count (:osaamismerkit oids)) "osaamismerkkiä sekä"
              (count (:oppilaitokset oids)) "oppilaitosta.")
    (let [ret (cond-> {}
                (contains? oids :koulutukset) (assoc :koulutukset (index-koulutukset (:koulutukset oids) execution-id))
                (contains? oids :toteutukset) (assoc :toteutukset (index-toteutukset (:toteutukset oids) execution-id))
                (contains? oids :haut) (assoc :haut (index-haut (:haut oids) execution-id))
                (contains? oids :hakukohteet) (assoc :hakukohteet (index-hakukohteet (:hakukohteet oids) execution-id))
                (contains? oids :sorakuvaukset) (assoc :sorakuvaukset (index-sorakuvaukset (:sorakuvaukset oids) execution-id))
                (contains? oids :valintaperusteet) (assoc :valintaperusteet (index-valintaperusteet (:valintaperusteet oids) execution-id))
                (contains? oids :eperusteet) (assoc :eperusteet (index-eperusteet (:eperusteet oids) execution-id))
                (contains? oids :oppilaitokset) (assoc :oppilaitokset (index-oppilaitokset (:oppilaitokset oids) execution-id))
                (contains? oids :osaamismerkit) (assoc :osaamismerkit (index-osaamismerkit (:osaamismerkit oids) execution-id)))]
      (log/info (str " ID:" execution-id " Indeksointi valmis. Aikaa kului " (- (. System (currentTimeMillis)) start) " ms."))
      ret)))

(defn index-since-kouta
  [since]
  (let [start-and-execution-id (. System (currentTimeMillis))
        date (long->rfc1123 since)
        oids (kouta-backend/get-last-modified date)]
    (log/info (str "ID:" start-and-execution-id " Indeksoidaan kouta-backendistä " (long->rfc1123 since) " jälkeen muuttuneet, (o)ids: " oids))
    (index-oids oids start-and-execution-id)
    (log/info (str "ID:" start-and-execution-id " Indeksointi valmis ja oidien haku valmis. Aikaa kului " (- (. System (currentTimeMillis)) start-and-execution-id) " ms."))))

(defn index-all-kouta
  []
  (let [start-and-execution-id (str "MASSA-" (. System (currentTimeMillis)))
        oids (kouta-backend/all-kouta-oids)
        oppilaitos-oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index
                                  (hierarkia/get-hierarkia-cached)
                                  (:oppilaitokset oids))]
    (log/info (str "ID:" start-and-execution-id " Indeksoidaan kouta-backendistä kaikki."))
    (let [koulutus-entries (koulutus/do-index (:koulutukset oids) start-and-execution-id)
          not-poistetut-koulutus-entries (filter not-poistettu? koulutus-entries)]
      (eperuste/do-index (eperuste-ids-on-koulutukset not-poistetut-koulutus-entries) start-and-execution-id)
      (tutkinnonosa/do-index (tutkinnonosa-ids-on-koulutukset not-poistetut-koulutus-entries) start-and-execution-id))
    (koulutus-search/do-index (:koulutukset oids) start-and-execution-id)
    (toteutus/do-index (:toteutukset oids) start-and-execution-id)
    (haku/do-index (:haut oids) start-and-execution-id)
    (hakukohde/do-index (:hakukohteet oids) start-and-execution-id)
    (valintaperuste/do-index (:valintaperusteet oids) start-and-execution-id)
    (oppilaitos/do-index oppilaitos-oids-to-index start-and-execution-id)
    (sorakuvaus/do-index (:sorakuvaukset oids) start-and-execution-id)
    (oppilaitos-search/do-index (:oppilaitokset oids) start-and-execution-id)
    (log/info (str "ID:" start-and-execution-id " Indeksointi valmis ja oidien haku valmis. Aikaa kului " (- (. System (currentTimeMillis)) (Long. (re-find #"\d+" start-and-execution-id))) " ms."))))

(defn index-all-koulutukset
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
        (index-koulutukset (:koulutukset (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-toteutukset
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
        (index-toteutukset (:toteutukset (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-haut
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (index-haut (:haut (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-hakukohteet
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (index-hakukohteet (:hakukohteet (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-valintaperusteet
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (index-valintaperusteet (:valintaperusteet (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-sorakuvaukset
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (index-sorakuvaukset (:sorakuvaukset (kouta-backend/all-kouta-oids)) execution-id)))

(defn index-all-osaamismerkit
  []
  (let [osaamismerkit (osaamismerkki-client/fetch-all)
        execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (log/info "ID:" execution-id " Indeksoidaan " (count osaamismerkit) " osaamismerkkiä, koodiUrit: " osaamismerkit)
    (index-osaamismerkit osaamismerkit execution-id)))

(defn index-all-oppilaitokset
  []
  (hierarkia/clear-all-cached-data)
  (let [oppilaitokset (hierarkia/get-all-indexable-oppilaitos-oids)
        execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (log/info "ID:" execution-id " Indeksoidaan " (count oppilaitokset) " oppilaitosta, (o)ids: " oppilaitokset)
    (index-oppilaitokset oppilaitokset execution-id false)))

(defn index-all-lokalisoinnit
  []
  (let [execution-id (str "MASSA-" (. System (currentTimeMillis)))]
    (log/info "ID:" execution-id " Indeksoidaan lokalisoinnit kaikilla kielillä.")
  (index-lokalisoinnit ["fi" "sv" "en"] execution-id)))
