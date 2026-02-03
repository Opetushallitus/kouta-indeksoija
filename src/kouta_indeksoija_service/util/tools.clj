(ns kouta-indeksoija-service.util.tools
  (:require [clojure.string :refer [blank? split]]))

(defn uuid
  []
  (.toString (java.util.UUID/randomUUID)))

(defn get-id
  [doc]
  (or (:oid doc) (:id doc)))

(defn comma-separated-string->vec
  [s]
  (vec (remove blank? (some-> s (split #",")))))

(defn ->distinct-vec
  [coll]
  (vec (distinct (remove nil? coll))))

(defn get-esitysnimi
  [entity]
  (get-in entity [:_enrichedData :esitysnimi] (:nimi entity)))

(defn assoc-hakukohde-nimi-as-esitysnimi
  [hakukohde]
  (assoc hakukohde :nimi (get-esitysnimi hakukohde)))

(defn- kouta-organisaatio-jarjestaa-urheilijan-amm-koulutusta?
  [kouta-organisaatio]
  (boolean (get-in kouta-organisaatio [:metadata :jarjestaaUrheilijanAmmKoulutusta])))

(defn oppilaitos-jarjestaa-urheilijan-amm-koulutusta?
  [oppilaitos]
  (boolean
    (or (kouta-organisaatio-jarjestaa-urheilijan-amm-koulutusta? (:oppilaitos oppilaitos))
      (some #(kouta-organisaatio-jarjestaa-urheilijan-amm-koulutusta? (:oppilaitoksenOsa %)) (:osat oppilaitos)))))

(defn get-oids
  [key coll]
  (set (remove clojure.string/blank? (map key coll))))
