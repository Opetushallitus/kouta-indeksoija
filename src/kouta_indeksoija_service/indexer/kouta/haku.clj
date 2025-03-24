(ns kouta-indeksoija-service.indexer.kouta.haku
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.tools.koodisto :as koodisto]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.tools.general :as general]
            [clojure.string :as str]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

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


(def maksullinen-kk-haku-start-year 2025)

(def maksullinen-kk-haku-start-date
  "Application payments are only charged from admissions starting on certain day"
  (t/from-time-zone (t/date-time maksullinen-kk-haku-start-year 1 1) (t/time-zone-for-id "Europe/Helsinki")))

(defn- maksullinen-kk-haku? [haku johtaa-tutkintoon?]
  (let [hakuajat-start (map :alkaa (:hakuajat haku))
        studies-start-term (general/koodiuri-wo-version (get-in haku [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamiskausi :koodiUri] ""))
        studies-start-year (Integer/parseInt (get-in haku [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamisvuosi] "0"))
        kohdejoukko (general/koodiuri-wo-version (get-in haku [:kohdejoukko :koodiUri] ""))
        kohdejoukon-tarkenne (general/koodiuri-wo-version (get-in haku [:kohdejoukonTarkenne :koodiUri] ""))]
    (boolean
      (and
        (or (and (contains? #{"kausi_k" "kausi_s"} studies-start-term) (> studies-start-year maksullinen-kk-haku-start-year))
            (and (= studies-start-term "kausi_s") (= studies-start-year maksullinen-kk-haku-start-year)))
        (some #(not (t/before? (common/parse-date-time %) maksullinen-kk-haku-start-date)) hakuajat-start)
        ; Kohdejoukko must be korkeakoulutus
        (= "haunkohdejoukko_12" kohdejoukko)
        ; "Kohdejoukon tarkenne must be empty or siirtohaku
        (or (str/blank? kohdejoukon-tarkenne)
            (= "haunkohdejoukontarkenne_1" kohdejoukon-tarkenne))
        ; Must be tutkintoon johtava
        johtaa-tutkintoon?))))

(defn create-index-entry
  [oid execution-id]
  (let [hakukohde-list-raw (kouta-backend/list-hakukohteet-by-haku-with-cache oid execution-id)
        haku (assoc (common/complete-entry (kouta-backend/get-haku-with-cache oid execution-id)) :hakukohteet hakukohde-list-raw)]
    (if (general/not-poistettu? haku)
      (let [toteutus-list  (common/complete-entries (kouta-backend/list-toteutukset-by-haku-with-cache oid execution-id))
            any-toteutus-johtaa-tutkintoon? (some #(= true (:johtaaTutkintoon %)) toteutus-list)
            assoc-toteutus (fn [h] (assoc h :toteutus (-> (common/assoc-organisaatiot
                                                            (first (filter #(= (:oid %) (:toteutusOid h))
                                                                           toteutus-list)))
                                                          (dissoc :johtaaTutkintoon))))
            hakukohde-list (vec (map (fn [hk] (-> hk
                                                  (general/set-hakukohde-tila-by-related-haku haku)
                                                  (koodisto/assoc-hakukohde-nimi-from-koodi)
                                                  (common/complete-entry)
                                                  (assoc-toteutus)))
                                     (filter general/not-poistettu? hakukohde-list-raw)))]
        (indexable/->index-entry-with-forwarded-data oid (-> haku
                                                             (assoc :hakukohteet hakukohde-list)
                                                             (assoc-paatelty-hakuvuosi-ja-hakukausi-for-hakukohde)
                                                             (conj (common/create-hakulomake-linkki-for-haku haku (:oid haku)))
                                                             (common/localize-dates)
                                                             (general/remove-version-from-koodiuri [:hakutapa :koodiUri])
                                                             (general/remove-version-from-koodiuri [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamiskausi :koodiUri])
                                                             (assoc :maksullinenKkHaku (maksullinen-kk-haku? haku any-toteutus-johtaa-tutkintoon?)))
                                                     haku))
      (indexable/->delete-entry-with-forwarded-data oid haku))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
