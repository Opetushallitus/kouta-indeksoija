(ns kouta-indeksoija-service.rest.oppijanumerorekisteri
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.rest.cas.session :refer [init-session cas-authenticated-request-as-json]]
            [kouta-indeksoija-service.rest.util :refer [handle-error]]
            [kouta-indeksoija-service.util.cache :refer [with-fifo-ttl-cache]]
            [clojure.tools.logging :as log]))

(defonce cas-session (init-session (resolve-url :oppijanumerorekisteri-service.internal.base) true))

(defonce cas-authenticated-get-as-json (partial cas-authenticated-request-as-json cas-session :get))

(defonce henkilo_cache_time_millis (* 1000 60 60 2)) ;2 tunnin cache

(defonce henkilo_cache_size 10000)

(defn- get-henkilo
  [oid]
  (try
    (-> (resolve-url :oppijanumerorekisteri-service.henkilo.oid oid)
        (cas-authenticated-get-as-json))
    (catch Exception e
      (log/info e "Exception when parsing ONR result"))))

(defn- parse-henkilo-nimi
  [henkilo]
  (str (or (:kutsumanimi henkilo) (:etunimet henkilo)) " " (:sukunimi henkilo)))

(def get-henkilo-nimi-with-cache
  (with-fifo-ttl-cache
    (fn [oid]
      (when-let [henkilo (get-henkilo oid)]
        (parse-henkilo-nimi henkilo)))
    henkilo_cache_time_millis
    henkilo_cache_size))
