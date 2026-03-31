(ns kouta-indeksoija-service.rest.organisaatio
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.rest.util :refer [get->json-body]]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.util.time :as time]
            [clojure.string :as s]))

(defonce oph-oid "1.2.246.562.10.00000000001")

(defn get-all-organisaatiot
  []
  (let [url (resolve-url :organisaatio-service.api.oid.jalkelaiset oph-oid)]
    (log/info "Fetching all organisaatiot from organisaatio service " url)
    (get->json-body url)))

(defn find-last-changes
  [last-modified]
  (let [date-string (time/long->date-time-string last-modified)
        res (->> (get->json-body (resolve-url :organisaatio-service.api.muutetut) {:lastModifiedSince date-string})
                 (filter #(not (s/starts-with? (:oid %) "1.2.246.562.28"))))]
    (when (seq res)
      (log/info "Found " (count res) " changes since " date-string " from organisaatiopalvelu"))
    res))

(defn get-by-oid
  [oid]
  (get->json-body (resolve-url :organisaatio-service.api.oid oid)))
