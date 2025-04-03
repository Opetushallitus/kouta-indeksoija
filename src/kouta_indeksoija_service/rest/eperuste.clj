(ns kouta-indeksoija-service.rest.eperuste
  (:refer-clojure :exclude [find])
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.util.cache :refer [with-fifo-ttl-cache]]
            [kouta-indeksoija-service.rest.util :refer [get->json-body]]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.util.time :as time]))

(defn- get-perusteet-page [page-nr last-modified]
  (let [params (cond-> {:sivu page-nr :sivukoko 100 :tuleva true :siirtyma true :voimassaolo true :poistunut true}
                 (not (nil? last-modified)) (assoc :muokattu last-modified))]
    (get->json-body (resolve-url :eperusteet-service.perusteet) params)))

(defn- indexable-eperuste?
  [eperuste]
  (let [indexable-koulutustyypit ["koulutustyyppi_1" "koulutustyyppi_2" "koulutustyyppi_11" "koulutustyyppi_12" "koulutustyyppi_26"]
        koulutustyyppi (:koulutustyyppi eperuste)]
    (some #(= % koulutustyyppi) indexable-koulutustyypit)))

(defn- filter-eperusteet-page
  [page]
  (assoc page :data (filterv indexable-eperuste? (:data page))))

(defn- find
  ([last-modified]
   (loop [page-nr 0 result []]
     (let [data (filter-eperusteet-page (or (get-perusteet-page page-nr last-modified) {:data [] :sivuja -1}))
           total-result (vec (conj result (map #(-> % :id str) (:data data))))]
       (if (<= (:sivuja data) (+ 1 page-nr))
         (flatten total-result)
         (recur (+ 1 page-nr) total-result)))))
  ([] (find nil)))

(defn get-doc
  [eperuste-id]
  (when eperuste-id
    (get->json-body
     (resolve-url :eperusteet-service.peruste.kaikki eperuste-id))))

(def get-doc-with-cache
  (with-fifo-ttl-cache get-doc (* 1000 60 5) 1000)) ;;5min cache, 1000 entries

(defn get-tutkinnonosa
  [tutkinnonosa-id]
  (get->json-body
   (resolve-url :eperusteet-service.internal.api.tutkinnonosa tutkinnonosa-id)))

(defn get-osaamisalakuvaukset-response
  [eperuste-id]
  (get->json-body (resolve-url :eperusteet-service.peruste.osaamisalakuvaukset eperuste-id)))

(defn get-osaamisalakuvaukset
  [eperuste-id eperuste-tila]
  (when-let [res (get-osaamisalakuvaukset-response eperuste-id)]
    (let [suoritustavat (keys res)
          osaamisalat (fn [suoritustapa] (apply concat (-> res suoritustapa vals)))
          assoc-values (fn [suoritustapa osaamisala] (assoc osaamisala :suoritustapa suoritustapa
                                                            :type "osaamisalakuvaus"
                                                            :oid (:id osaamisala)
                                                            :eperuste-oid eperuste-id
                                                            :tila eperuste-tila))]
      (vec (flatten (map (fn [st] (map (partial assoc-values st) (osaamisalat st))) suoritustavat))))))

(defn find-all
  []
  (let [res (find)]
    (log/info (str "Found total " (count res) " docs from ePerusteet"))
    res))

(defn find-changes
  [last-modified]
  (let [res (find last-modified)]
    (when (seq res)
      (log/info (str "Found " (count res) " changes since " (time/long->date-time-string (long last-modified)) " from ePerusteet")))
    res))

(defn- search-and-get-first-eperuste
  [params]
  (let [r (get->json-body (resolve-url :eperusteet-service.perusteet) params)]
    (when-let [id (some-> r :data (first) :id)]
      (get-doc id))))

(defn get-by-koulutuskoodi
  [koulutuskoodi]
  (or (search-and-get-first-eperuste {:tuleva false :siirtyma false :voimassaolo true :poistunut false :koulutuskoodi koulutuskoodi})
      (search-and-get-first-eperuste {:tuleva false :siirtyma true :voimassaolo false :poistunut false :koulutuskoodi koulutuskoodi})))