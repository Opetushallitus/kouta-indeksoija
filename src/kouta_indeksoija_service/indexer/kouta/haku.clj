(ns kouta-indeksoija-service.indexer.kouta.haku
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.tools.general :as general]
            [kouta-indeksoija-service.util.tools :refer [assoc-hakukohde-nimi-as-esitysnimi]]
            [clj-time.format :as f]
            [clj-time.core :as t]))

(def index-name "haku-kouta")

(defn- parse-hakuaika [hakuaika]
  {:alkaa (f/parse (:alkaa hakuaika))
   :paattyy (f/parse (:paattyy hakuaika))})

(defn- assoc-paatelty-hakuvuosi-ja-hakukausi-for-hakukohde [haku]
  (if-let [hakuaika (first (sort-by :alkaa (map parse-hakuaika (:hakuajat haku))))]
    (-> haku
        (assoc :hakuvuosi (or (some-> (:paattyy hakuaika)
                                      t/year)
                              (t/year (:alkaa hakuaika))))
        (assoc :hakukausi (if (>= (t/month (:alkaa hakuaika)) 8)
                            "kausi_s#1"
                            "kausi_k#1")))
    haku))

(defn create-index-entry
  [oid execution-id]
  (let [hakukohde-list-raw (kouta-backend/list-hakukohteet-by-haku-with-cache oid execution-id)
        haku (assoc (common/complete-entry (kouta-backend/get-haku-with-cache oid execution-id)) :hakukohteet hakukohde-list-raw)]
    (if (general/not-poistettu? haku)
      (let [toteutus-list  (common/complete-entries (kouta-backend/list-toteutukset-by-haku-with-cache oid execution-id))
            assoc-toteutus (fn [h] (assoc h :toteutus
                                          (common/assoc-organisaatiot
                                            (first (filter #(= (:oid %) (:toteutusOid h)) toteutus-list)))))
            hakukohde-list (vec (map (fn [hk] (-> hk
                                                  (general/set-hakukohde-tila-by-related-haku haku)
                                                  (assoc-hakukohde-nimi-as-esitysnimi)
                                                  (common/complete-entry)
                                                  (assoc-toteutus)))
                                     (filter general/not-poistettu? hakukohde-list-raw)))]
        (indexable/->index-entry-with-forwarded-data oid (-> haku
                                                             (assoc :hakukohteet hakukohde-list)
                                                             (assoc-paatelty-hakuvuosi-ja-hakukausi-for-hakukohde)
                                                             (conj (common/create-hakulomake-linkki-for-haku haku (:oid haku)))
                                                             (common/localize-dates)
                                                             (general/remove-version-from-koodiuri [:hakutapa :koodiUri])
                                                             (general/remove-version-from-koodiuri [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamiskausi :koodiUri]))
                                                     haku))
      (indexable/->delete-entry-with-forwarded-data oid haku))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
