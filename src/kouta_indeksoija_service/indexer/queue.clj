(ns kouta-indeksoija-service.indexer.queue
  (:require [kouta-indeksoija-service.elastic.queue :refer [reset-queue upsert-to-queue]]
            [kouta-indeksoija-service.rest.tarjonta :as tarjonta-client]
            [kouta-indeksoija-service.rest.organisaatio :as organisaatio-client]
            [kouta-indeksoija-service.rest.eperuste :as eperusteet-client]
            [clojure.tools.logging :as log]))


(defn- find-docs
  [index oid]
  (cond
    (= "organisaatio" index) (organisaatio-client/find-docs oid)
    :else [{:type index :oid oid}]))

(defn queue-all
  []
  (log/info "Tyhjennetään indeksointijono ja uudelleenindeksoidaan kaikki data Tarjonnasta, eperusteista ja organisaatiopalvelusta.")
  (reset-queue)
  (let [tarjonta-docs (tarjonta-client/find-all-tarjonta-docs)
        organisaatio-docs (organisaatio-client/find-docs nil)
        eperusteet-docs (eperusteet-client/find-all)
        docs (clojure.set/union tarjonta-docs organisaatio-docs eperusteet-docs)]
    (log/info "Saving" (count docs) "items to index-queue" (flatten (for [[k v] (group-by :type docs)] [(count v) k]) ))
    (upsert-to-queue docs)))

(defn queue-all-eperusteet
  []
  (let [eperusteet-docs (eperusteet-client/find-all)]
    (log/info "Lisätään indeksoijan jonoon " (count eperusteet-docs) " eperustetta")
    (upsert-to-queue eperusteet-docs)))

(defn queue-all-organisaatiot
  []
  (let [organisaatio-docs (organisaatio-client/find-docs nil)]
    (log/info "Lisätään indeksoijan jonoon " (count organisaatio-docs) " organisaatiota")
    (upsert-to-queue organisaatio-docs)))

(defn queue
  [index oid]
  (let [docs (find-docs index oid)
        related-koulutus (flatten (map tarjonta-client/get-related-koulutus docs))
        docs-with-related-koulutus (remove nil? (clojure.set/union docs related-koulutus))]
    (upsert-to-queue docs-with-related-koulutus)))

(defn empty-queue []
  (reset-queue))