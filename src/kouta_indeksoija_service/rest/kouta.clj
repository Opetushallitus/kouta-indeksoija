(ns kouta-indeksoija-service.rest.kouta
  (:require [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.util.time :refer [long->rfc1123]]
            [kouta-indeksoija-service.rest.cas.session :refer [init-session cas-authenticated-request-as-json]]
            [ring.util.codec :refer [url-encode]]
            [kouta-indeksoija-service.util.cache :refer [with-fifo-ttl-cache]]
            [cheshire.core :as json]
            [kouta-indeksoija-service.indexer.tools.general :as general]
            [kouta-indeksoija-service.util.conf :refer [env]]
            [kouta-indeksoija-service.util.tools :refer [assoc-hakukohde-nimi-as-esitysnimi]]))

(defonce cas-session
  (init-session (resolve-url :kouta-backend.auth-login) false))

(defonce cas-authenticated-get-as-json
  (partial cas-authenticated-request-as-json cas-session :get))
(defonce cas-authenticated-post-as-json
  (partial cas-authenticated-request-as-json cas-session :post))

(defonce kouta-cache-time-millis
  (* 1000 (Integer. (:kouta-indeksoija-kouta-cache-time-seconds env))))
(defonce kouta-cache-size
  (Integer. (:kouta-indeksoija-kouta-cache-size env)))

(defn get-last-modified
  [since]
  (cas-authenticated-get-as-json (resolve-url :kouta-backend.modified-since
                                              (url-encode since))
                                 {:query-params {:lastModified since}}))

(defn all-kouta-oids
  []
  (get-last-modified (long->rfc1123 0)))

(defn- get-doc
  [type oid execution-id]
  (let [url-keyword (keyword
                      (str "kouta-backend."
                           type
                           (if (or (= "valintaperuste" type)
                                   (= "sorakuvaus" type))
                             ".id"
                             ".oid")))]
    (cas-authenticated-get-as-json
      (resolve-url url-keyword oid) {:query-params {:myosPoistetut "true"}})))

(def get-doc-with-cache
  (with-fifo-ttl-cache get-doc kouta-cache-time-millis kouta-cache-size))

(defn get-koulutus-with-cache
  [oid execution-id]
  (get-doc-with-cache "koulutus" oid execution-id))

(defn get-toteutus-with-cache
  [oid execution-id]
  (get-doc-with-cache "toteutus" oid execution-id))

(defn get-haku-with-cache
  [oid execution-id]
  (get-doc-with-cache "haku" oid execution-id))

(defn get-hakukohde-with-cache
  [oid execution-id]
  (get-doc-with-cache "hakukohde" oid execution-id))

;lukiolinjakoodi saa olla arvo koodistoista "lukiopainotukset" tai
;"lukiolinjaterityinenkoulutustehtava"
(defn- get-pistehistoria
  [tarjoaja-oid hakukohdekoodi lukiolinjakoodi execution-id]
  (if (and (nil? hakukohdekoodi) (nil? lukiolinjakoodi))
    []
    (cas-authenticated-get-as-json
      (resolve-url :kouta-backend.pistehistoria)
      (if (some? hakukohdekoodi)
        {:query-params {:tarjoaja tarjoaja-oid
                        :hakukohdekoodi hakukohdekoodi}}
        {:query-params {:tarjoaja tarjoaja-oid
                        :lukiolinjakoodi lukiolinjakoodi}}))))

(def get-pistehistoria-with-cache
  (with-fifo-ttl-cache
    get-pistehistoria kouta-cache-time-millis kouta-cache-size))

;Käytännössä tällä löytyy tietoa vain toisen asteen hakukohteille
;HUOM! Hakukohde voi olla koutan hakukohde tai hakutiedon hakukohde!
(defn get-pistehistoria-for-hakukohde [hakukohde execution-id]
  (let [jarjestyspaikkaOid (:jarjestyspaikkaOid hakukohde)
        lukiolinja (get-in hakukohde [:metadata :hakukohteenLinja]
                           (:hakukohteenLinja hakukohde))
        lukiolinjaKoodiUri (:linja lukiolinja)
        hakukohdeKoodiUri (or (:hakukohdeKoodiUri hakukohde)
                              (when (and (some? lukiolinja)
                                         (nil? lukiolinjaKoodiUri))
                                    "hakukohteet_000"))]
    (when (and (some? jarjestyspaikkaOid)
               (or (some? hakukohdeKoodiUri)
                   (some? lukiolinjaKoodiUri)))
      (get-pistehistoria-with-cache
        jarjestyspaikkaOid hakukohdeKoodiUri lukiolinjaKoodiUri execution-id))))

(defn- get-hakukohde-oids-by-jarjestyspaikat
  [oids execution-id]
  (cas-authenticated-post-as-json
    (resolve-url :kouta-backend.jarjestyspaikat.hakukohde-oids)
    {:body (json/generate-string oids) :content-type :json}))

(def get-hakukohde-oids-by-jarjestyspaikat-with-cache
  (with-fifo-ttl-cache get-hakukohde-oids-by-jarjestyspaikat
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-toteutus-oids-by-tarjoajat
  [oids execution-id]
  (cas-authenticated-post-as-json
    (resolve-url :kouta-backend.tarjoajat.toteutus-oids)
    {:body (json/generate-string oids) :content-type :json}))

(def get-toteutus-oids-by-tarjoajat-with-cache
  (with-fifo-ttl-cache get-toteutus-oids-by-tarjoajat
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-koulutus-oids-by-tarjoajat
  [oids]
  (cas-authenticated-post-as-json
   (resolve-url :kouta-backend.tarjoajat.koulutus-oids)
   {:body (json/generate-string oids) :content-type :json}))

(def get-koulutus-oids-by-tarjoajat-with-cache
  (with-fifo-ttl-cache
    get-koulutus-oids-by-tarjoajat
    kouta-cache-time-millis
    kouta-cache-size))

(defn get-valintaperuste-with-cache
  [id execution-id]
  (get-doc-with-cache "valintaperuste" id execution-id))

(defn get-sorakuvaus-with-cache
  [id execution-id]
  (if (some? id) (get-doc-with-cache "sorakuvaus" id execution-id) nil))

(defn- get-oppilaitos
  ([oid execution-id]
   (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.oppilaitos.oid oid)
    {}))
  ([oid yhteystiedotForOsat execution-id]
   (cas-authenticated-get-as-json
     (resolve-url :kouta-backend.oppilaitos.oid oid)
     {:query-params {:yhteystiedotForOsat yhteystiedotForOsat}})))

(def get-oppilaitos-with-cache
  (with-fifo-ttl-cache get-oppilaitos kouta-cache-time-millis kouta-cache-size))

(defn- get-oppilaitokset
  [oids execution-id]
  (cas-authenticated-post-as-json
    (resolve-url :kouta-backend.oppilaitos.oppilaitokset)
    {:body (json/generate-string oids) :content-type :json}))

(def get-oppilaitokset-with-cache
  (with-fifo-ttl-cache get-oppilaitokset
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-toteutus-list-for-koulutus
  ([koulutus-oid vainJulkaistut execution-id]
   (cas-authenticated-get-as-json
     (resolve-url :kouta-backend.koulutus.toteutukset koulutus-oid)
     {:query-params {:vainJulkaistut vainJulkaistut}}))
  ([koulutus-oid execution-id]
   (get-toteutus-list-for-koulutus koulutus-oid false execution-id)))

(def get-toteutus-list-for-koulutus-with-cache
  (with-fifo-ttl-cache get-toteutus-list-for-koulutus
    kouta-cache-time-millis
    kouta-cache-size))

(defn- get-koulutukset-by-tarjoaja
  [oppilaitos-oid execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.tarjoaja.koulutukset oppilaitos-oid)))

(def get-koulutukset-by-tarjoaja-with-cache
  (with-fifo-ttl-cache get-koulutukset-by-tarjoaja
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- assoc-pistehistoria [hakukohde pistehistoria]
  (if (nil? pistehistoria)
    hakukohde
    (assoc-in hakukohde [:metadata :pistehistoria] pistehistoria)))

(defn- get-hakutiedot-for-koulutus
  [koulutus-oid execution-id]
  (let [response (cas-authenticated-get-as-json
                   (resolve-url :kouta-backend.koulutus.hakutiedot
                                koulutus-oid))]
    (if response
      (map
        (fn [hakutieto]
          (assoc
            hakutieto
            :haut
            (map
              (fn [haku]
                (assoc
                  haku
                  :hakukohteet
                  (map
                    (fn [hakukohde]
                      (let [pistehistoria
                            (get-pistehistoria-for-hakukohde hakukohde
                                                             execution-id)]
                        (-> hakukohde
                            (assoc-pistehistoria pistehistoria)
                            (assoc-hakukohde-nimi-as-esitysnimi)
                            (general/set-hakukohde-tila-by-related-haku haku))))
                   (:hakukohteet haku))))
              (:haut hakutieto))))
        response)
      response)))

(def get-hakutiedot-for-koulutus-with-cache
  (with-fifo-ttl-cache get-hakutiedot-for-koulutus
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-haut-by-toteutus
  [toteutus-oid execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.toteutus.haut-list toteutus-oid)
    {:query-params {:vainOlemassaolevat "false"}}))

(def list-haut-by-toteutus-with-cache
  (with-fifo-ttl-cache list-haut-by-toteutus
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-hakukohteet-by-haku
  [haku-oid execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.haku.hakukohteet-list haku-oid)
    {:query-params {:vainOlemassaolevat "false"}}))

(def list-hakukohteet-by-haku-with-cache
  (with-fifo-ttl-cache list-hakukohteet-by-haku
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-toteutukset-by-haku
  [haku-oid execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.haku.toteutukset-list haku-oid)))

(def list-toteutukset-by-haku-with-cache
  (with-fifo-ttl-cache list-toteutukset-by-haku
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-hakukohteet-by-valintaperuste
  [valintaperuste-id execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.valintaperuste.hakukohteet-list
                 valintaperuste-id)
    {:query-params {:vainOlemassaolevat "false"}}))

(def list-hakukohteet-by-valintaperuste-with-cache
  (with-fifo-ttl-cache list-hakukohteet-by-valintaperuste
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-koulutus-oids-by-sorakuvaus
  [sorakuvaus-id execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.sorakuvaus.koulutukset-list sorakuvaus-id)
    {:query-params {:vainOlemassaolevat "false"}}))

(def list-koulutus-oids-by-sorakuvaus-with-cache
  (with-fifo-ttl-cache list-koulutus-oids-by-sorakuvaus
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-oppilaitoksen-osat
  [oppilaitos-oid execution-id]
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.oppilaitos.osat oppilaitos-oid)))

(def get-oppilaitoksen-osat-with-cache
  (with-fifo-ttl-cache get-oppilaitoksen-osat
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-toteutukset
  [oids execution-id]
  (cas-authenticated-post-as-json
    (resolve-url :kouta-backend.toteutukset)
    {:body (json/generate-string oids) :content-type :json}))

(def get-toteutukset-with-cache
  (with-fifo-ttl-cache get-toteutukset
    kouta-cache-time-millis
    kouta-cache-size))

(defn- get-koulutukset
  [oids execution-id]
  (cas-authenticated-post-as-json
   (resolve-url :kouta-backend.koulutukset)
   {:body (json/generate-string oids) :content-type :json}))

(def get-koulutukset-with-cache
  (with-fifo-ttl-cache get-koulutukset
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- get-opintokokonaisuudet-by-toteutus-oids
  [oids execution-id]
  (cas-authenticated-post-as-json
    (resolve-url :kouta-backend.opintokokonaisuudet)
    {:body (json/generate-string oids) :content-type :json}))

(def get-opintokokonaisuudet-by-toteutus-oids-with-cache
  (with-fifo-ttl-cache get-opintokokonaisuudet-by-toteutus-oids
                       kouta-cache-time-millis
                       kouta-cache-size))

(defn- list-used-eperuste-ids
  []
  (cas-authenticated-get-as-json
    (resolve-url :kouta-backend.koulutukset.eperuste-ids)))

(def list-used-eperuste-ids-with-cache
  (with-fifo-ttl-cache list-used-eperuste-ids
                       kouta-cache-time-millis
                       kouta-cache-size))
