(ns kouta-indeksoija-service.rest.osaamismerkki
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.rest.util :refer [get->json-body]]
            [kouta-indeksoija-service.util.cache :refer [with-fifo-ttl-cache]]
            [clojure.tools.logging :as log]))

(defn- fetch-osaamismerkit
  []
  (map #(:koodiUri %) (get->json-body (resolve-url :eperusteet-service.osaamismerkit))))

(defn fetch-all
  []
  (let [res (fetch-osaamismerkit)]
    (log/info (str "Found a total of " (count res) " osaamismerkki from ePerusteet"))
    res))

(defn get-doc
  [osaamismerkki-koodi-uri]
  (when osaamismerkki-koodi-uri
    (get->json-body
     (resolve-url :eperusteet-service.osaamismerkki osaamismerkki-koodi-uri))))

(def get-doc-with-cache
  (with-fifo-ttl-cache get-doc (* 1000 60 5) 1000)) ;;5min cache, 1000 entries
