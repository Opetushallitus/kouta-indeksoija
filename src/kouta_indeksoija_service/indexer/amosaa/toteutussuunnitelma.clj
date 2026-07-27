(ns kouta-indeksoija-service.indexer.amosaa.toteutussuunnitelma
  (:require [kouta-indeksoija-service.rest.eperuste :refer [get-opetussuunnitelma-with-cache
                                                             get-paikalliset-tutkinnonosat-with-cache]]
            [kouta-indeksoija-service.indexer.indexable :as indexable]))

(def index-name "toteutussuunnitelma")

(defn create-index-entry
  [opetussuunnitelma-id execution-id]
  (when-let [suunnitelma (get-opetussuunnitelma-with-cache opetussuunnitelma-id)]
    (let [tutkinnonosat (get-paikalliset-tutkinnonosat-with-cache opetussuunnitelma-id)
          id (str opetussuunnitelma-id)]
      (indexable/->index-entry id (assoc suunnitelma
                                         :oid id
                                         :paikallisetTutkinnonOsat tutkinnonosat
                                         :tyyppi "toteutussuunnitelma")))))

(defn do-index
  [opetussuunnitelma-ids execution-id]
  (indexable/do-index index-name opetussuunnitelma-ids create-index-entry execution-id))

(defn get-from-index
  [opetussuunnitelma-id]
  (indexable/get index-name opetussuunnitelma-id))
