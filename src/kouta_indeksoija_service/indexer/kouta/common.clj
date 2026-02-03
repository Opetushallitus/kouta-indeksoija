(ns kouta-indeksoija-service.indexer.kouta.common
  (:refer-clojure :exclude [replace])
  (:require [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.rest.oppijanumerorekisteri :refer [get-henkilo-nimi-with-cache]]
            [kouta-indeksoija-service.util.time :as time]
            [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.util.tools :refer [get-esitysnimi]]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [clojure.string :refer [capitalize replace trim]]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [postwalk]]
            [clojure.set :as set]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as hierarkia-cache]))

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
  (re-find (re-pattern "^\\w+_\\S+(#\\d{1,3})?$") (trim value)))

(def excluded-fields {:externalId true
                      :tunniste true
                      :arvo true
                      :fi true
                      :sv true
                      :en true
                      :osaamismerkki true})

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

(defn is-postinumerokoodiuri?
  [value]
  (and
    (string? value)
    (boolean
      (re-find
        (re-pattern "^posti_\\d+#?\\d?")
        value))))

(defn- process-map-entry-for-koodis [map-entry]
  (let [[k v]               map-entry
        allowed-key?        (not (get excluded-fields (keyword k)))
        interesting-values? (processable-as-koodi-uri? v)]
    (when (and interesting-values? (not allowed-key?) (not (is-postinumerokoodiuri? v)))
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

(defn- process-map-entry-for-postinumerokoodis [map-entry]
  (let [[k v] map-entry]
    (if (and (string? v) (processable-as-koodi-uri? v) (is-postinumerokoodiuri? v))
      (let [koodi (process-koodi-values v)
            nimet (get koodi :nimi)
            nimi (or (get nimet k) (get nimet :fi) (get nimet :sv) (get nimet :en))
            koodiUri (get koodi :koodiUri)]
        [k {:koodiUri koodiUri
            :nimi nimi}])
      map-entry)))

(defn- enrich-postinumerokoodi-values [value]
  (if (map-entry? value)
    (process-map-entry-for-postinumerokoodis value)
    value))

(defn decorate-postinumerokoodiuris
  [x]
  (postwalk #(-> % enrich-postinumerokoodi-values) x))

(defn- get-tarjoaja
  [oid]
  (when-let [tarjoaja (hierarkia-cache/get-hierarkia-item oid)]
    (-> tarjoaja
      (assoc :paikkakunta (get-koodi-nimi-with-cache "kunta" (:kotipaikkaUri tarjoaja)))
      (select-keys [:oid :nimi :paikkakunta]))))

(defn assoc-organisaatio
  [entry]
  (if-let [oid (:organisaatioOid entry)]
    (assoc (dissoc entry :organisaatioOid) :organisaatio (get-tarjoaja oid))
    entry))

(defn- jarjestyspaikka-hierarkia-nimi
  [oid name]
  (let [tarjoaja (hierarkia-cache/get-hierarkia-item oid)]
    (if (organisaatio-tool/oppilaitos? tarjoaja)
      name
      (if-let [parent (hierarkia-cache/get-hierarkia-item (:parentOid tarjoaja))]
        (jarjestyspaikka-hierarkia-nimi
          (:parentOid tarjoaja)
          (reduce
            #(assoc %1 %2 (str
                            (or (get-in parent [:nimi %2])
                                (get-in parent [:nimi :fi]))
                            ", "
                            (or (get %1 %2)
                                (get name :fi))))
            name
            [:fi :sv :en]))
        name))))

(defn assoc-jarjestyspaikka
  [entry]
  (if-let [oid (:jarjestyspaikkaOid entry)]
    (let [tarjoaja (get-tarjoaja oid)]
      (-> entry
          (assoc :jarjestyspaikka (assoc tarjoaja
                                    :jarjestaaUrheilijanAmmKoulutusta
                                    (get-in entry [:jarjestaaUrheilijanAmmKoulutusta])))
          (assoc :jarjestyspaikkaHierarkiaNimi (jarjestyspaikka-hierarkia-nimi oid (:nimi tarjoaja)))
          (dissoc entry :jarjestyspaikkaOid :jarjestaaUrheilijanAmmKoulutusta)))
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
    (assoc entry :tarjoajat (remove nil? (map #(get-tarjoaja %1) oids)))
    entry))

(defn assoc-organisaatiot
  [entry]
  (let [organisaatio (get-in entry [:organisaatio :oid])
        tarjoajat (map :oid (:tarjoajat entry))]
    (assoc entry :organisaatiot (vec (distinct (remove nil? (conj tarjoajat organisaatio)))))))

(defn localize-dates [form]
  (let [format-date-kws (fn [tree dates]
                          (loop [d dates
                                 t tree]
                            (if-let [date (first d)]
                              (if-let [aika (date t)]
                                (recur (rest d)
                                       (assoc t (keyword (str "formatoitu" (capitalize (name date)))) (time/format-localized-date aika)))
                                (recur (rest d)
                                       t))
                              t)))]
    (postwalk #(-> %
                   (format-date-kws [:koulutuksenAlkamispaivamaara :koulutuksenPaattymispaivamaara :liitteidenToimitusaika :toimitusaika :modified :paattyy :alkaa])) form)))

(defn clean-langs-not-in-kielivalinta [form]
  (let [langs #{:fi :sv :en}
        used-langs (set (map #(keyword %) (:kielivalinta form)))
        langs-to-clean (set/difference langs used-langs)
        dissoc-langs (fn [x] (if (and (map? x) (not-empty (clojure.set/difference (set (keys x)) langs-to-clean)))
                               (apply dissoc x langs-to-clean)
                               x))]
    (if (and
          (< 0 (count langs-to-clean))
          (< (count langs-to-clean) 3))
      (postwalk dissoc-langs form)
      form)))

(defn- clean-enriched-data
  [map]
  (postwalk
    #(if (map? %)
      (dissoc % :_enrichedData)
      %)
    map))

(defn remove-empty-p-tags [entry]
  (clojure.walk/postwalk
    #(if (map? %)
       (into {} (filter (fn [[key val]] (not= "<p></p>" val)) %))
       %)
    entry))

(defn complete-entry
  [entry]
  (-> entry
      (remove-empty-p-tags)
      (clean-langs-not-in-kielivalinta)
      (clean-enriched-data)
      (decorate-koodi-uris)
      (decorate-postinumerokoodiuris)
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
      (select-keys [:oid :organisaatio :tila :tarjoajat :muokkaaja :modified :organisaatiot])
      (assoc :nimi (get-esitysnimi toteutus))
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

(defn create-sort-names [nimi]
  {:fi (or (not-empty (:fi nimi))
           (not-empty (:sv nimi))
           (not-empty (:en nimi)))
   :sv (or (not-empty (:sv nimi))
           (not-empty (:fi nimi))
           (not-empty (:en nimi)))
   :en (or (not-empty (:en nimi))
           (not-empty (:fi nimi))
           (not-empty (:sv nimi)))})

(defn get-toimipisteen-toteutukset
  [organisaatio-oid toteutukset]
  (->> (for [toteutus toteutukset]
         (when-let [indexable-oids (not-empty (filter #(= organisaatio-oid %) (:tarjoajat toteutus)))]
           (assoc toteutus :tarjoajat indexable-oids)))
       (remove nil?)
       (vec)))

(defn get-organisaation-koulutukset
  [organisaatio koulutukset]
  (if (organisaatio-tool/toimipiste? organisaatio)
    (into {} (for [[koulutus-oid koulutus] koulutukset
                   ;; filtteröidään pois koulutukset, joiden toteutuksen tarjoaja
                   ;; ei ole indeksoitavana oleva toimipiste
                   :let [toimipisteen-toteutukset (get-toimipisteen-toteutukset (:oid organisaatio)
                                                                                (:toteutukset koulutus))
                         koulutus-with-toimipisteen-toteutukset (assoc koulutus :toteutukset toimipisteen-toteutukset)]
                   :when (seq toimipisteen-toteutukset)]
               [koulutus-oid koulutus-with-toimipisteen-toteutukset]))
    koulutukset))

(defn assoc-nimi-from-oppilaitoksen-yhteystiedot
  [oppilaitos yhteystiedot]
  (if-let [oppilaitoksen-nimi (:nimi yhteystiedot)]
    (assoc oppilaitos :nimi oppilaitoksen-nimi)
    oppilaitos))
