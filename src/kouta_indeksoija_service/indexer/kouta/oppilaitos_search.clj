(ns kouta-indeksoija-service.indexer.kouta.oppilaitos-search
  (:require [clojure.string :as string]
            [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache list-alakoodi-nimet-with-cache]]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec get-esitysnimi]]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.kouta.common :refer [assoc-nimi-from-oppilaitoksen-yhteystiedot
                                                                   create-sort-names
                                                                   get-organisaation-koulutukset]]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :refer [assoc-koulutusohjelmatLkm]]
            [kouta-indeksoija-service.indexer.tools.general :refer [ammatillinen? amm-tutkinnon-osa? julkaistu? luonnos?]]
            [kouta-indeksoija-service.indexer.tools.search :as search-tool]))

(def index-name "oppilaitos-kouta-search")

(defn- tarjoaja-organisaatiot
  [oppilaitos tarjoajat]
  (vec (map #(organisaatio-tool/find-from-organisaatio-and-children oppilaitos %) tarjoajat)))

(defn- tutkintonimikkeet-for-osaamisala
  [osaamisalaKoodiUri]
  (list-alakoodi-nimet-with-cache osaamisalaKoodiUri "tutkintonimikkeet"))

(defn- tutkintonimikkeet-for-toteutus
  [toteutus]
  ;TODO -> eperusteet
  (if (ammatillinen? toteutus)
    (->> (get-in toteutus [:metadata :osaamisalat :koodiUri])
         (mapcat tutkintonimikkeet-for-osaamisala)
         (->distinct-vec))
    []))

(defn koulutus-search-terms
  [oppilaitos koulutus]
  (search-tool/search-terms :koulutus koulutus
                            :tarjoajat (tarjoaja-organisaatiot oppilaitos (:tarjoajat koulutus))
                            :oppilaitos oppilaitos
                            :opetuskieliUrit (:kieletUris oppilaitos)
                            :koulutustyypit (search-tool/deduce-koulutustyypit koulutus oppilaitos)
                            :kuva (:teemakuva koulutus)
                            :nimi (:nimi koulutus)
                            :onkoTuleva true
                            :metadata (cond-> {:tutkintonimikkeetKoodiUrit      (search-tool/tutkintonimike-koodi-urit koulutus)
                                               :opintojenLaajuusKoodiUri        (search-tool/opintojen-laajuus-koodi-uri koulutus)
                                               :opintojenLaajuusyksikkoKoodiUri (search-tool/opintojen-laajuusyksikko-koodi-uri koulutus)
                                               :opintojenLaajuusNumero          (search-tool/opintojen-laajuus-numero koulutus)
                                               :opintojenLaajuusNumeroMin       (search-tool/opintojen-laajuus-numero-min koulutus)
                                               :opintojenLaajuusNumeroMax       (search-tool/opintojen-laajuus-numero-max koulutus)
                                               :koulutustyypitKoodiUrit         (search-tool/koulutustyyppi-koodi-urit koulutus)
                                               :koulutustyyppi                  (:koulutustyyppi koulutus)}
                                        (amm-tutkinnon-osa? koulutus) (assoc :tutkinnonOsat (search-tool/tutkinnon-osat koulutus)))))

(defn toteutus-search-terms
  [oppilaitos koulutus hakutiedot toteutus]
  (let [hakutieto (search-tool/get-toteutuksen-julkaistut-hakutiedot hakutiedot toteutus)
        toteutus-metadata (:metadata toteutus)
        tarjoajat (tarjoaja-organisaatiot oppilaitos (:tarjoajat toteutus))
        opetus (get-in toteutus [:metadata :opetus])
        jarjestaa-urheilijan-amm-koulutusta (search-tool/jarjestaako-tarjoaja-urheilijan-amm-koulutusta
                                             (:tarjoajat toteutus)
                                             (:haut hakutieto))]
    (search-tool/search-terms :koulutus koulutus
                              :toteutus toteutus
                              :tarjoajat tarjoajat
                              :jarjestaa-urheilijan-amm-koulutusta jarjestaa-urheilijan-amm-koulutusta
                              :oppilaitos oppilaitos
                              :hakutiedot hakutieto
                              :toteutus-organisaationimi (remove nil? (distinct (map :nimi tarjoajat)))
                              :opetuskieliUrit (get-in toteutus [:metadata :opetus :opetuskieliKoodiUrit])
                              :koulutustyypit (search-tool/deduce-koulutustyypit koulutus oppilaitos toteutus-metadata)
                              :kuva (:teemakuva toteutus)
                              :nimi (get-esitysnimi toteutus)
                              :onkoTuleva false
                              :metadata (merge {:tutkintonimikkeet   (tutkintonimikkeet-for-toteutus toteutus)
                                                :opetusajatKoodiUrit (:opetusaikaKoodiUrit opetus)
                                                :suunniteltuKestoKuukausina (search-tool/kesto-kuukausina opetus)
                                                :onkoApuraha         (:onkoApuraha opetus)
                                                :koulutustyyppi      (:koulutustyyppi koulutus)}
                                               (search-tool/get-maksullisuus-search-data (:maksut opetus))))))

(defn search-terms
  [oppilaitos koulutus toteutus hakutiedot]
  (when (not (nil? koulutus))
    (if (or (nil? toteutus) (luonnos? toteutus))
      (koulutus-search-terms oppilaitos koulutus)
      (toteutus-search-terms oppilaitos koulutus hakutiedot toteutus))))

(defn- get-kouta-oppilaitos
  [oid execution-id]
  (let [oppilaitos (kouta-backend/get-oppilaitos-with-cache oid execution-id)]
    (when (julkaistu? oppilaitos)
      {:kielivalinta (:kielivalinta oppilaitos)
       :kuvaus       (get-in oppilaitos [:metadata :esittely])
       :logo         (:logo oppilaitos)})))

(defn- create-base-entry
  [oppilaitos koulutukset execution-id]
  (-> oppilaitos
      (select-keys [:oid :nimi :organisaatiotyypit])
      (merge (get-kouta-oppilaitos (:oid oppilaitos) execution-id))
      (assoc :nimi_sort (create-sort-names (:nimi oppilaitos)))
      (assoc-koulutusohjelmatLkm koulutukset)))

(defn- assoc-paikkakunnat
  [entry]
  (let [paikkakuntaKoodiUrit (vec (distinct (filter #(string/starts-with? % "kunta") (mapcat :sijainti (:search_terms entry)))))]
    (assoc entry :paikkakunnat (vec (map get-koodi-nimi-with-cache paikkakuntaKoodiUrit)))))

(defn create-search-terms
  [oppilaitos koulutukset execution-id]
  (remove nil?
          (flatten
           (for [[_ koulutus] koulutukset
                 :let [koulutuksen-toteutukset (:toteutukset koulutus)
                       hakutiedot (kouta-backend/get-hakutiedot-for-koulutus-with-cache
                                   (:oid koulutus)
                                   execution-id)]]
             (when (seq koulutuksen-toteutukset)
               (map #(search-terms oppilaitos koulutus % hakutiedot) koulutuksen-toteutukset))))))

(defn- create-oppilaitos-entry-with-hits
  [oppilaitos koulutukset execution-id]
  (let [oppilaitoksen-koulutukset (get-organisaation-koulutukset oppilaitos koulutukset)]
    (-> oppilaitos
        (create-base-entry oppilaitoksen-koulutukset execution-id)
        (assoc :search_terms (create-search-terms oppilaitos oppilaitoksen-koulutukset execution-id))
        (assoc-paikkakunnat))))

(defn create-index-entry
  [oid execution-id]
  (when-let [oppilaitos (cache/find-oppilaitos-by-oid oid)]
    (let [oppilaitoksen-yhteystiedot-from-organisaatiopalvelu (cache/get-yhteystiedot oid)
          oppilaitos-with-updated-nimi (assoc-nimi-from-oppilaitoksen-yhteystiedot
                                        oppilaitos
                                        oppilaitoksen-yhteystiedot-from-organisaatiopalvelu)
          oppilaitos-oid (if (organisaatio-tool/toimipiste? oppilaitos) (:parentOid oppilaitos) (:oid oppilaitos))
          ;; jos toimipiste, haetaan koulutukset parentin oidilla, koska toimipiste ei ole
          ;; koulutuksen vaan toteutuksen tarjoaja
          koulutukset (kouta-backend/get-koulutukset-by-tarjoaja-with-cache oppilaitos-oid execution-id)
          entry (create-oppilaitos-entry-with-hits oppilaitos-with-updated-nimi koulutukset execution-id)]
      (if (and (organisaatio-tool/indexable? oppilaitos) (> (get-in entry [:koulutusohjelmatLkm :kaikki]) 0))
        (indexable/->index-entry (:oid entry) entry)
        (indexable/->delete-entry (:oid entry))))))

(defn do-index
  ([oids execution-id]
   (do-index oids execution-id true))
  ([oids execution-id clear-cache-before]
   (when (= true clear-cache-before)
     (cache/clear-all-cached-data))
   (let [oids-to-index (organisaatio-tool/resolve-organisaatio-oids-to-index (cache/get-hierarkia-cached) oids)]
     (indexable/do-index index-name oids-to-index create-index-entry execution-id))))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
