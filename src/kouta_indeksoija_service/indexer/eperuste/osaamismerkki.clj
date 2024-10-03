(ns kouta-indeksoija-service.indexer.eperuste.osaamismerkki
  (:require [kouta-indeksoija-service.rest.osaamismerkki :as osaamismerkki-client]
            [kouta-indeksoija-service.indexer.indexable :as indexable]))

(def index-name "osaamismerkki")

(defn create-index-entry
  [koodi-uri _]
  (when-let [osaamismerkki (osaamismerkki-client/get-doc-with-cache koodi-uri)]
    (let [id (:koodiUri osaamismerkki)]
      (indexable/->index-entry id (assoc osaamismerkki :tyyppi "osaamismerkki")))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [koodi-uri]
  (indexable/get index-name koodi-uri))
