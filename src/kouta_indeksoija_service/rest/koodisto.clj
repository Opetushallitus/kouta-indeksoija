(ns kouta-indeksoija-service.rest.koodisto
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [clj-log.error-log :refer [with-error-logging]]
            [kouta-indeksoija-service.rest.util :refer [get->json-body]]
            [clojure.tools.logging :as log]
            [clojure.core.memoize :as memo]
            [clojure.string :as str]))

(defn extract-versio
  [koodi-uri]
  (if-let [i (str/index-of koodi-uri "#")]
    {:koodi (subs koodi-uri 0 i)
     :versio (subs koodi-uri (+ i 1))}
    {:koodi koodi-uri}))

(defn extract-koodi-nimi
  [koodi]
  (reduce #(assoc %1 (keyword (str/lower-case (:kieli %2))) (:nimi %2)) {} (:metadata koodi)))

(defn- get-koodi-with-url
  [url]
  (get->json-body url))

(defn get-koodit
  [koodisto]
  (when koodisto
    (get-koodi-with-url (resolve-url :koodisto-service.koodisto-koodit koodisto))))

(def get-koodit-with-cache
  (memo/ttl get-koodit {} :ttl/threshold (* 1000 60 30))) ;30 minuutin cache

(defn get-koodi
  [koodisto koodi-uri]
  (when koodi-uri
    (let [with-versio (extract-versio koodi-uri)]
      (get-koodi-with-url (resolve-url :koodisto-service.koodisto-koodi koodisto (:koodi with-versio))))))

(def get-koodi-with-cache
  (memo/ttl get-koodi {} :ttl/threshold (* 1000 60 30))) ;30 minuutin cache

(defn get-koodi-nimi-with-cache
  ([koodisto koodi-uri]
   {:koodiUri koodi-uri
    :nimi (extract-koodi-nimi (get-koodi-with-cache koodisto koodi-uri))})
  ([koodi-uri]
   (when koodi-uri
     (get-koodi-nimi-with-cache (subs koodi-uri 0 (str/index-of koodi-uri "_")) koodi-uri))))

(defn get-alakoodit
  [koodi-uri]
  (when koodi-uri
    (let [with-versio (extract-versio koodi-uri)]
      (if (contains? with-versio :versio)
        (get-koodi-with-url (resolve-url :koodisto-service.alakoodit-koodi-versio (:koodi with-versio) (:versio with-versio)))
        (get-koodi-with-url (resolve-url :koodisto-service.alakoodit (:koodi with-versio)))))))

(def get-alakoodit-with-cache
  (memo/ttl get-alakoodit {} :ttl/threshold (* 1000 60 30))) ;30 minuutin cache

(defn list-alakoodit-with-cache
  [koodi-uri alakoodi-uri]
  (when (and koodi-uri alakoodi-uri)
    (filter #(= (get-in % [:koodisto :koodistoUri]) alakoodi-uri) (get-alakoodit-with-cache koodi-uri))))

(defn list-alakoodi-nimet-with-cache
  [koodi-uri alakoodi-uri]
  (let [alakoodi->nimi-json (fn [alakoodi] {:koodiUri (:koodiUri alakoodi)
                                            :nimi     (extract-koodi-nimi alakoodi)})]
    (vec (map alakoodi->nimi-json (list-alakoodit-with-cache koodi-uri alakoodi-uri)))))

(defn get-alakoodi-nimi-with-cache
  [koodi-uri alakoodi-uri]
  (first (list-alakoodi-nimet-with-cache koodi-uri alakoodi-uri)))