(ns kouta-indeksoija-service.indexer.kouta.common
  (:refer-clojure :exclude [replace])
  (:require [kouta-indeksoija-service.rest.kouta :refer [get-koulutus]]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.indexer.cache.tarjoaja :as tarjoaja]
            [kouta-indeksoija-service.rest.oppijanumerorekisteri :refer [get-henkilo-nimi-with-cache]]
            [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [clojure.string :refer [replace]]
            [clojure.walk :refer [postwalk]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- strip-koodi-uri-key
  [key]
  (if (keyword? key)
    (-> key
        (name)
        (replace "KoodiUrit" "")
        (replace "KoodiUri" "")
        (replace "Uri" "")
        (keyword))
    key))

(defn koodi-uri? ;Etsitään koodiUria kaavalla KIRJAIMIANUMEROITA_NONWHITESPACEMERKKEJÄ#NUMERO
  [value]         ;Numeroita voi olla 1-3 kpl
  (re-find (re-pattern "^\\w+_\\S+(#\\d{1,3})?$") (string/trim value)))

(def excluded-fields {:externalId true})

(defn- processable-as-koodi-uri? [v]
  (boolean (or (and (string? v) (koodi-uri? v))
               (and (coll? v) (some #(and (string? %) (koodi-uri? %)) v)))))

(defn- safe-get-koodi-for-value [value]
  (if (koodi-uri? value)
    (get-koodi-nimi-with-cache value)
    value))

(defn- process-koodi-values [input]
  (cond (vector? input) (mapv safe-get-koodi-for-value input)
        (seq? input) (doall (map safe-get-koodi-for-value input))
        :else (safe-get-koodi-for-value input)))

(defn- process-map-entry-for-koodis [map-entry]
  (let [[k v]               map-entry
        allowed-key?        (not (get excluded-fields (keyword k)))
        interesting-values? (processable-as-koodi-uri? v)]
    (when (and interesting-values? (not allowed-key?))
      (log/warn (str "Skip processing for map entry because of disallowed key" map-entry)))
    (if (and allowed-key? interesting-values?)
      [k (process-koodi-values v)]
      map-entry)))

(defn- enrich-koodi-values [value]
  (if (map-entry? value)
    (process-map-entry-for-koodis value)
    value))

(defn decorate-koodi-uris
  [x]
  (postwalk #(-> % strip-koodi-uri-key enrich-koodi-values) x))

(defn- get-tarjoaja
  [oid]
  (assoc (tarjoaja/get-tarjoaja oid) :oid oid))

(defn assoc-organisaatio
  [entry]
  (if-let [oid (:organisaatioOid entry)]
    (assoc (dissoc entry :organisaatioOid) :organisaatio (get-tarjoaja oid))
    entry))

(defn assoc-jarjestyspaikka
  [entry]
  (if-let [oid (:jarjestyspaikkaOid entry)]
    (assoc (dissoc entry :jarjestyspaikkaOid) :jarjestyspaikka (get-tarjoaja oid))
    entry))

(defn assoc-muokkaaja
  [entry]
  (if-let [oid (:muokkaaja entry)]
    (if-let [nimi (get-henkilo-nimi-with-cache oid)]
      (assoc entry :muokkaaja {:oid oid :nimi nimi})
      (assoc entry :muokkaaja {:oid oid}))
    entry))

(defn assoc-tarjoajat
  [entry]
  (if-let [oids (:tarjoajat entry)]
    (assoc entry :tarjoajat (map #(get-tarjoaja %1) oids))
    entry))

(defn assoc-organisaatiot
  [entry]
  (let [organisaatio (get-in entry [:organisaatio :oid])
        tarjoajat (map :oid (:tarjoajat entry))]
    (assoc entry :organisaatiot (vec (distinct (remove nil? (conj tarjoajat organisaatio)))))))

(defn complete-entry
  [entry]
  (-> entry
      (decorate-koodi-uris)
      (assoc-organisaatio)
      (assoc-tarjoajat)
      (assoc-jarjestyspaikka)
      (assoc-muokkaaja)))

(defn complete-entries
  [entries]
  (map complete-entry entries))

(defn toteutus->list-item
  [toteutus]
  (-> toteutus
      (select-keys [:oid :organisaatio :nimi :tila :tarjoajat :muokkaaja :modified :organisaatiot])
      (assoc-organisaatiot)))

(defn- create-ataru-link-for-haku
  [haku-oid lang]
  (resolve-url :ataru-hakija.ataru.hakulomake-for-haku haku-oid lang))

(defn- create-ataru-links-for-haku
  [haku-oid]
  (if haku-oid {:fi (create-ataru-link-for-haku haku-oid "fi")
                :sv (create-ataru-link-for-haku haku-oid "sv")
                :en (create-ataru-link-for-haku haku-oid "en")}))

(defn create-hakulomake-linkki-for-haku
  [hakulomaketiedot haku-oid]
  (when-let [linkki (case (:hakulomaketyyppi hakulomaketiedot)
                      "ataru" (create-ataru-links-for-haku haku-oid)
                      "muu"   (:hakulomakeLinkki hakulomaketiedot)
                      nil)]
    {:hakulomakeLinkki linkki}))

(defn- create-ataru-link-for-hakukohde
  [hakukohde-oid lang]
  (resolve-url :ataru-hakija.ataru.hakulomake-for-hakukohde hakukohde-oid lang))

(defn- create-ataru-links-for-hakukohde
  [hakukohde-oid]
  (when hakukohde-oid {:fi (create-ataru-link-for-hakukohde hakukohde-oid "fi")
                       :sv (create-ataru-link-for-hakukohde hakukohde-oid "sv")
                       :en (create-ataru-link-for-hakukohde hakukohde-oid "en")}))

(defn create-hakulomake-linkki-for-hakukohde
  [hakulomaketiedot hakukohde-oid]
  (when-let [linkki (case (:hakulomaketyyppi hakulomaketiedot)
                      "ataru" (create-ataru-links-for-hakukohde hakukohde-oid)
                      "muu"   (:hakulomakeLinkki hakulomaketiedot)
                      nil)]
    {:hakulomakeLinkki linkki}))