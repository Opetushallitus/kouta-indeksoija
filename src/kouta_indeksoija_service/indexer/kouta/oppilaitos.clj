(ns kouta-indeksoija-service.indexer.kouta.oppilaitos
  (:require [clojure.set :refer [rename-keys]]
            [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-and-arvo-with-cache]]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.tools.general :refer [julkaistu?]]
            [kouta-indeksoija-service.util.tools :refer [oppilaitos-jarjestaa-urheilijan-amm-koulutusta?]]
            [clojure.string :as s]))

(def index-name "oppilaitos-kouta")
(def languages ["fi" "en" "sv"])

(defn- organisaatio-entry
  [organisaatio]
  (-> organisaatio
      (select-keys [:nimi :kieletUris :kotipaikkaUri :status :organisaatiotyypit :oppilaitostyyppi :oid :parentToimipisteOid])
      (rename-keys {:kieletUris :opetuskieliKoodiUrit
                    :oppilaitostyyppi :oppilaitostyyppiKoodiUri
                    :kotipaikkaUri :kotipaikkaKoodiUri
                    :organisaatiotyypit :organisaatiotyyppiKoodiUrit})
      (common/complete-entry)))

(defn assoc-koulutusohjelmatLkm
  [organisaatio koulutukset]
  (let [koulutus-objects (map second koulutukset)
        julkaistut-toteutukset-count (fn [koulutus-object] (count (filter julkaistu? (:toteutukset koulutus-object))))
        koulutusohjelmat-lkm (reduce + (map julkaistut-toteutukset-count  koulutus-objects))
        tutkintoonJohtavat (if (< 0 koulutusohjelmat-lkm)
                             (reduce + (map julkaistut-toteutukset-count (filter #(:johtaaTutkintoon  %) koulutus-objects)))
                             0)]
    (assoc organisaatio
      :koulutusohjelmatLkm {:kaikki koulutusohjelmat-lkm
                            :tutkintoonJohtavat tutkintoonJohtavat
                            :eiTutkintoonJohtavat (- koulutusohjelmat-lkm tutkintoonJohtavat)})))
(defn- oppilaitos-entry
  [organisaatio oppilaitos koulutukset]
  (cond-> (assoc-koulutusohjelmatLkm (organisaatio-entry organisaatio) koulutukset)
    (seq oppilaitos) (assoc :oppilaitos (-> oppilaitos
                                            (common/complete-entry)
                                            (dissoc :oid)))))

(defn create-kielistetty-yhteystieto
  [yhteystieto-group yhteystieto-keyword languages]
  (into
    {}
    (for [lang languages
          :let [yhteystieto (yhteystieto-keyword
                              (first
                                (filter
                                  (fn [entry]
                                    (re-find (re-pattern (str "kieli_" lang)) (:kieli entry)))
                                  yhteystieto-group)))]
          :when (not (nil? yhteystieto))]
      [(keyword lang) yhteystieto])))

(defn create-kielistetty-osoitetieto
  [osoitetieto languages]
  {:osoite (create-kielistetty-yhteystieto osoitetieto :osoite languages)
   :postinumeroKoodiUri (create-kielistetty-yhteystieto osoitetieto :postinumeroUri languages)})

(defn create-kielistetty-osoite-str
  [osoitetieto ulkomainen_osoite_en languages]
  (let [katuosoite (create-kielistetty-yhteystieto osoitetieto :osoite languages)
        postinumero_uri (create-kielistetty-yhteystieto osoitetieto :postinumeroUri languages)
        postinumero (zipmap
                      (keys postinumero_uri)
                      (map #(re-find #"\d{5}" %) (vals postinumero_uri)))
        postitoimipaikka (create-kielistetty-yhteystieto osoitetieto :postitoimipaikka languages)
        capitalized_postitoimipaikka (zipmap
                                       (keys postitoimipaikka)
                                       (map #(clojure.string/capitalize %) (vals postitoimipaikka)))
        postinro_ja_toimipaikka (merge-with #(str %1 " " %2)
                                            postinumero
                                            capitalized_postitoimipaikka)
        kielistetyt_osoitteet (merge-with #(str %1 ", " %2)
                                          katuosoite
                                          postinro_ja_toimipaikka)]
    (if (and (not (:en kielistetyt_osoitteet)) (not-empty ulkomainen_osoite_en))
      (assoc kielistetyt_osoitteet :en (clojure.string/replace (:osoite (first ulkomainen_osoite_en)) #"\n" ", "))
      kielistetyt_osoitteet)))

(defn parse-yhteystiedot
  [response languages]
  (let [yhteystiedot (:yhteystiedot response)
        sahkopostit (filter (fn [yhteystieto] (get-in yhteystieto [:email])) yhteystiedot)
        puhelinnumerot (filter (fn [yhteystieto] (= "puhelin" (get-in yhteystieto [:tyyppi]))) yhteystiedot)
        postiosoitteet (filter (fn [yhteystieto] (= "posti" (get-in yhteystieto [:osoiteTyyppi]))) yhteystiedot)
        kayntiosoitteet (filter (fn [yhteystieto] (= "kaynti" (get-in yhteystieto [:osoiteTyyppi]))) yhteystiedot)
        ulkomainen_posti_en (filter (fn [yhteystieto] (and
                                                        (= "ulkomainen_posti" (get-in yhteystieto [:osoiteTyyppi]))
                                                        (re-find #"kieli_en" (get-in yhteystieto [:kieli]))))
                                    yhteystiedot)
        ulkomainen_kaynti_en (filter (fn [yhteystieto] (and
                                                        (= "ulkomainen_kaynti" (get-in yhteystieto [:osoiteTyyppi]))
                                                        (re-find #"kieli_en" (get-in yhteystieto [:kieli]))))
                                    yhteystiedot)]
    [{:nimi (:nimi response)
      :sahkoposti (create-kielistetty-yhteystieto sahkopostit :email languages)
      :puhelinnumero (create-kielistetty-yhteystieto puhelinnumerot :numero languages)
      :postiosoite (create-kielistetty-osoitetieto postiosoitteet languages)
      :kayntiosoite (create-kielistetty-osoitetieto kayntiosoitteet languages)
      :postiosoiteStr (create-kielistetty-osoite-str postiosoitteet ulkomainen_posti_en languages)
      :kayntiosoiteStr (create-kielistetty-osoite-str kayntiosoitteet ulkomainen_kaynti_en languages)}]))

(defn create-osoite-str-for-hakijapalvelut
  [katuosoite-map toimipaikat]
  (let [toimipaikat-with-default (into {} (for [[kieli _] katuosoite-map]
                                            (let [toimipaikan-nimet (get-in toimipaikat [kieli :nimi])]
                                              (if (contains? toimipaikan-nimet kieli)
                                                [kieli (get toimipaikan-nimet kieli)]
                                                (if (get-in toimipaikat [kieli])
                                                  [kieli (or (get toimipaikan-nimet :fi) (get toimipaikan-nimet :sv) (get toimipaikan-nimet :en))]
                                                  nil)))))
        capitalized_toimipaikat (zipmap
                                 (keys toimipaikat-with-default)
                                 (map #(if (not (nil? %))
                                         (clojure.string/capitalize %)
                                         %)
                                      (vals toimipaikat-with-default)))]
    (merge-with #(str %1 " " %2)
                (into {} (for [[kieli v] katuosoite-map] [kieli (if (get-in toimipaikat [kieli])
                                                                  (str v ", " (or (get-in toimipaikat [kieli :koodiArvo]) ""))
                                                                  (str v))]))
                capitalized_toimipaikat)))

(defn add-osoite-str-to-yhteystiedot
  [yhteystiedot-from-oppilaitos-metadata osoitetyyppi json-key]
  (if-let [osoite (get-in yhteystiedot-from-oppilaitos-metadata [osoitetyyppi])]
    (if-let [postinumeroKoodiUrit (or (get-in osoite [:postinumeroKoodiUri])
                                     (get-in osoite [:postinumero :koodiUri]))]
      (let [postitoimipaikat (doall (into {} (for [[kieli v] postinumeroKoodiUrit]
                                               (if (seq v)
                                                   [kieli (get-koodi-nimi-and-arvo-with-cache "posti" v)]
                                                   nil))))
            osoite-str (create-osoite-str-for-hakijapalvelut (get-in osoite [:osoite]) postitoimipaikat)]
        (assoc yhteystiedot-from-oppilaitos-metadata json-key osoite-str))
      yhteystiedot-from-oppilaitos-metadata)
    yhteystiedot-from-oppilaitos-metadata))

(defn- oppilaitoksen-osa-entry
  [organisaatio oppilaitoksen-osa]
  ; TODO oppilaitosten osat eivät voi käyttää assoc-koulutusohjelmia sillä kouta-backend/get-koulutukset-by-tarjoaja ei palauta osille mitään
  ; TODO oppilaitoksen osien pitäisi päätellä koulutusohjelmia-lkm eri reittiä: toteutukset -> koulutukset -> johtaaTutkintoon
  (let [update-yhteystiedot-fn (fn [oo] (if (nil? (get-in oo [:metadata :hakijapalveluidenYhteystiedot]))
                                          oo (update-in oo [:metadata :hakijapalveluidenYhteystiedot]
                                                        (fn [yhteystiedot] (-> yhteystiedot
                                                                               (add-osoite-str-to-yhteystiedot :postiosoite :postiosoiteStr)
                                                                               (add-osoite-str-to-yhteystiedot :kayntiosoite :kayntiosoiteStr))))))]
    (cond-> (organisaatio-entry organisaatio)
      (seq oppilaitoksen-osa) (assoc :oppilaitoksenOsa (-> oppilaitoksen-osa
                                                           (common/complete-entry)
                                                           (update-yhteystiedot-fn)
                                                           (dissoc :oppilaitosOid :oid))))))

(defn- add-data-from-organisaatio-palvelu
  [oppilaitoksen-osa organisaatio]
  (prn "organisaatio")
  (prn organisaatio)
  (prn oppilaitoksen-osa)
  (let [org-from-organisaatio-palvelu (cache/get-yhteystiedot (:oid oppilaitoksen-osa))
        ;; yhteystiedot (parse-yhteystiedot org-from-organisaatio-palvelu languages)
        yhteystiedot (-> (get-in organisaatio [:yhteystiedot])
                         (add-osoite-str-to-yhteystiedot :postiosoite :postiosoiteStr)
                         (add-osoite-str-to-yhteystiedot :kayntiosoite :kayntiosoiteStr))
        hakijapalveluiden-yhteystiedot (-> (get-in oppilaitoksen-osa [:metadata :hakijapalveluidenYhteystiedot])
                                           (add-osoite-str-to-yhteystiedot :postiosoite :postiosoiteStr)
                                           (add-osoite-str-to-yhteystiedot :kayntiosoite :kayntiosoiteStr))]
    (prn "yhteystiedot")
    (prn yhteystiedot)
    (-> oppilaitoksen-osa
        (assoc :status (:status org-from-organisaatio-palvelu))
        (assoc-in [:metadata :yhteystiedot] yhteystiedot)
        (assoc-in [:metadata :hakijapalveluidenYhteystiedot] hakijapalveluiden-yhteystiedot))))

(defn- find-child-from-organisaatio-children
  [oid oppilaitos-organisaatio]
  (or
    (first (filter #(= oid (:oid %)) (get-in
                                       oppilaitos-organisaatio
                                       [:children])))
    {}))

(defn- oppilaitos-entry-with-osat
  [organisaatio koulutukset execution-id]
  ;; (prn "organisaatio")
  ;; (prn organisaatio)
  (let [oppilaitos-oid (:oid organisaatio)
        oppilaitos (or (kouta-backend/get-oppilaitos-with-cache oppilaitos-oid true execution-id) {})
        oppilaitos-organisaatio-with-children (get-in oppilaitos [:_enrichedData :organisaatio])
        oppilaitoksen-yhteystiedot-from-organisaatiopalvelu (cache/get-yhteystiedot oppilaitos-oid)
        yhteystiedot (-> (get-in oppilaitos [:_enrichedData :organisaatio :yhteystiedot])
                         (add-osoite-str-to-yhteystiedot :postiosoite :postiosoiteStr)
                         (add-osoite-str-to-yhteystiedot :kayntiosoite :kayntiosoiteStr))
        hakijapalveluiden-yhteystiedot (-> (get-in oppilaitos [:metadata :hakijapalveluidenYhteystiedot])
                                           (add-osoite-str-to-yhteystiedot :postiosoite :postiosoiteStr)
                                           (add-osoite-str-to-yhteystiedot :kayntiosoite :kayntiosoiteStr))
        oppilaitos-metadata (assoc
                              (get-in oppilaitos [:metadata])
                              :yhteystiedot yhteystiedot
                              :hakijapalveluidenYhteystiedot hakijapalveluiden-yhteystiedot)
        enriched-oppilaitos (assoc oppilaitos :metadata oppilaitos-metadata)
        ;; organisaatio-servicen /jalkelaiset-rajapinta palauttaa lyhytNimen oppilaitoksen osille
        ;; käytetään org yhteystietojen mukana tulevaa kokonimeä
        organisaatio-with-updated-nimi (common/assoc-nimi-from-oppilaitoksen-yhteystiedot
                                         organisaatio
                                         oppilaitoksen-yhteystiedot-from-organisaatiopalvelu)
        oppilaitoksen-osat (map #(add-data-from-organisaatio-palvelu % (find-child-from-organisaatio-children
                                                                         (get-in % [:oid])
                                                                         oppilaitos-organisaatio-with-children))
                                (kouta-backend/get-oppilaitoksen-osat-with-cache oppilaitos-oid execution-id))
        oppilaitoksen-koulutukset (common/get-organisaation-koulutukset organisaatio koulutukset)
        find-oppilaitoksen-osa (fn [child] (or (first (filter #(= (:oid %) (:oid child)) oppilaitoksen-osat)) {}))]
    (prn "oppilaitos")
    (prn oppilaitos)
    ;; (prn "oppilaitoksen-osat")
    ;; (prn oppilaitoksen-osat)
    (as-> (oppilaitos-entry organisaatio-with-updated-nimi enriched-oppilaitos oppilaitoksen-koulutukset) o
      (assoc o :osat (->> (organisaatio-tool/get-indexable-children organisaatio)
                          (map #(oppilaitoksen-osa-entry % (find-oppilaitoksen-osa %)))
                          (vec)))
      (assoc o :jarjestaaUrheilijanAmmKoulutusta (oppilaitos-jarjestaa-urheilijan-amm-koulutusta? o)))))

(defn create-index-entry
  [oid execution-id]
  (when-let [organisaatio (cache/find-oppilaitos-by-oid oid)]
    (let [;; jos toimipiste, haetaan koulutukset parentin oidilla, koska toimipiste ei ole
          ;; koulutuksen vaan toteutuksen tarjoaja
          oppilaitos-oid (if (organisaatio-tool/toimipiste? organisaatio) (:parentOid organisaatio) (:oid organisaatio))
          koulutukset (kouta-backend/get-koulutukset-by-tarjoaja-with-cache oppilaitos-oid execution-id)
          entry (oppilaitos-entry-with-osat organisaatio koulutukset execution-id)]
      ;; (prn "entry")
      ;; (prn entry)
      (if (organisaatio-tool/indexable? organisaatio)
        (indexable/->index-entry (:oid organisaatio) entry)
        (indexable/->delete-entry (:oid organisaatio))))))

(defn do-index
  ([oids execution-id]
   (do-index oids execution-id true))
  ([oids execution-id clear-cache-before]
   (when (= true clear-cache-before)
      (cache/clear-all-cached-data))
    (let [oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index (cache/get-hierarkia-cached) oids)]
      (indexable/do-index index-name oids-to-index create-index-entry execution-id))))

(defn get-from-index
  [oid & query-params]
  (apply indexable/get index-name oid query-params))
