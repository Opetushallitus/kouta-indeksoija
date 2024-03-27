(ns kouta-indeksoija-service.rest.koodisto
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.util.cache :refer [with-fifo-ttl-cache]]
            [kouta-indeksoija-service.rest.util :refer [get->json-body]]
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
  (with-fifo-ttl-cache get-koodit (* 1000 60 30) 1000)) ;30 minuutin cache, 1000 entryä

(defn get-koodi
  [koodisto koodi-uri]
  (when koodi-uri
    (let [with-versio (extract-versio koodi-uri)]
      (get-koodi-with-url (resolve-url :koodisto-service.koodisto-koodi koodisto (:koodi with-versio))))))

(def get-koodi-with-cache
  (with-fifo-ttl-cache get-koodi (* 1000 60 30) 10000)) ;30 minuutin cache, 10000 entryä

(defn get-koodi-nimi-with-cache
  ([koodisto koodi-uri]
   {:koodiUri koodi-uri
    :nimi (extract-koodi-nimi (get-koodi-with-cache koodisto koodi-uri))})
  ([koodi-uri]
   (when koodi-uri
     (get-koodi-nimi-with-cache (subs koodi-uri 0 (str/index-of koodi-uri "_")) koodi-uri))))

(defn get-koodi-nimi-and-arvo-with-cache
  [koodisto koodi-uri]
   (let [koodi (get-koodi-with-cache koodisto koodi-uri)]
     {:koodiUri koodi-uri
      :koodiArvo (get-in koodi [:koodiArvo])
      :nimi (extract-koodi-nimi koodi)}))

(defn get-alakoodit
  [koodi-uri]
  (when koodi-uri
    (let [with-versio (extract-versio koodi-uri)]
      (if (contains? with-versio :versio)
        (get-koodi-with-url (resolve-url :koodisto-service.alakoodit-koodi-versio (:koodi with-versio) (:versio with-versio)))
        (get-koodi-with-url (resolve-url :koodisto-service.alakoodit (:koodi with-versio)))))))

(defn get-ylakoodit
  [koodi-uri]
  (when koodi-uri
    (let [with-versio (extract-versio koodi-uri)]
      (if (contains? with-versio :versio)
        (get-koodi-with-url (resolve-url :koodisto-service.ylakoodit-koodi-versio (:koodi with-versio) (:versio with-versio)))
        (get-koodi-with-url (resolve-url :koodisto-service.ylakoodit (:koodi with-versio)))))))

(def get-alakoodit-with-cache
  (with-fifo-ttl-cache get-alakoodit (* 1000 60 30) 10000)) ;30 minuutin cache, 10000 entryä

(def get-ylakoodit-with-cache
  (with-fifo-ttl-cache get-ylakoodit (* 1000 60 30) 10000)) ;30 minuutin cache, 10000 entryä

(defn list-alakoodit-with-cache
  [koodi-uri alakoodisto-uri]
  (when (and koodi-uri alakoodisto-uri)
    (filter #(= (get-in % [:koodisto :koodistoUri]) alakoodisto-uri) (get-alakoodit-with-cache koodi-uri))))

(defn list-alakoodi-nimet-with-cache
  [koodi-uri alakoodisto-uri]
  (let [alakoodi->nimi-json (fn [alakoodi] {:koodiUri (:koodiUri alakoodi)
                                            :nimi     (extract-koodi-nimi alakoodi)})]
    (vec (map alakoodi->nimi-json (list-alakoodit-with-cache koodi-uri alakoodisto-uri)))))

(defn get-alakoodi-nimi-with-cache
  [koodi-uri alakoodisto-uri]
  (first (list-alakoodi-nimet-with-cache koodi-uri alakoodisto-uri)))

(defn list-ylakoodit-with-cache
  [koodi-uri ylakoodisto-uri]
  (when (and koodi-uri ylakoodisto-uri)
    (filter #(= (get-in % [:koodisto :koodistoUri]) ylakoodisto-uri) (get-ylakoodit-with-cache koodi-uri))))
