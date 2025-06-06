(ns kouta-indeksoija-service.indexer.kouta.koulutus-search
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.indexer.tools.general :refer [amm-tutkinnon-osa? amm-osaamisala? julkaistu?]]
            [kouta-indeksoija-service.indexer.tools.search :as search-tool]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec get-esitysnimi]]))

(def index-name "koulutus-kouta-search")

(defn- get-logo-and-tila-from-kouta
  [oid execution-id]
  (when oid
    (kouta-backend/get-oppilaitos-with-cache oid execution-id)))

(defn- get-oppilaitos
  [hierarkia execution-id]
  (when-let [oppilaitos (organisaatio-tool/find-oppilaitos-from-hierarkia hierarkia)]
    (let [{:keys [tila logo]} (get-logo-and-tila-from-kouta (:oid oppilaitos) execution-id)]
      (cond->
          (assoc oppilaitos :tila tila)
          (julkaistu? {:tila tila}) (assoc :logo logo)))))

(defn- tuleva-jarjestaja?
  [hierarkia toteutukset]
  (-> (organisaatio-tool/filter-indexable-for-hierarkia hierarkia (mapcat :tarjoajat toteutukset))
      (->distinct-vec)
      (empty?)))

(defn- find-hierarkiat-for-indexable-oppilaitokset
  [tarjoajat]
  (->> tarjoajat
       (map cache/find-oppilaitos-by-own-or-child-oid)
       (remove nil?)
       (filter organisaatio-tool/indexable?)
       (->distinct-vec)))

(defn- jarjestaja-search-terms
  [hierarkia koulutus toteutukset hakutiedot oppilaitos]
  (for [toteutus (->> toteutukset
                      (map (fn [t] (->> (:tarjoajat t)
                                        (organisaatio-tool/filter-indexable-for-hierarkia hierarkia)
                                        (assoc t :tarjoajat))))
                      (filter #(seq (:tarjoajat %))))
        :let [hakutieto (search-tool/get-toteutuksen-julkaistut-hakutiedot hakutiedot toteutus)]
        :let [opetus (get-in toteutus [:metadata :opetus])]
        :let [toteutus-metadata (:metadata toteutus)]]
    (search-tool/search-terms
      :koulutus koulutus
      :toteutus toteutus
      :oppilaitos oppilaitos
      :tarjoajat (:tarjoajat toteutus)
      :hakutiedot hakutieto
      :toteutus-organisaationimi (remove nil? (distinct (map :nimi (flatten (:tarjoajat toteutus)))))
      :opetuskieliUrit (:opetuskieliKoodiUrit opetus)
      :koulutustyypit (search-tool/deduce-koulutustyypit koulutus oppilaitos toteutus-metadata)
      :kuva (:logo oppilaitos)
      :nimi (:nimi oppilaitos)
      :onkoTuleva false
      :lukiopainotukset (remove nil? (distinct (map :koodiUri (:painotukset toteutus-metadata))))
      :lukiolinjat_er (remove nil? (distinct (map :koodiUri (:erityisetKoulutustehtavat toteutus-metadata))))
      :osaamisalat (remove nil? (distinct (map :koodiUri (:osaamisalat toteutus-metadata))))
      :metadata (merge
                  {:tutkintonimikkeetKoodiUrit (search-tool/tutkintonimike-koodi-urit koulutus)
                   :opetusajatKoodiUrit (:opetusaikaKoodiUrit opetus)
                   :maksullisuustyyppi (:maksullisuustyyppi opetus)
                   :maksunMaara (:maksunMaara opetus)
                   :suunniteltuKestoKuukausina (search-tool/kesto-kuukausina opetus)
                   :onkoApuraha (:onkoApuraha opetus)
                   :koulutustyyppi (:tyyppi toteutus-metadata)
                   :oppilaitosTila (:tila oppilaitos)
                   :jarjestaaUrheilijanAmmKoulutusta (search-tool/jarjestaako-toteutus-urheilijan-amm-koulutusta
                                                       (:haut hakutieto))}
                  (select-keys toteutus-metadata
                               [:ammatillinenPerustutkintoErityisopetuksena
                                :jarjestetaanErityisopetuksena
                                :opintojenLaajuusNumero
                                :opintojenLaajuusNumeroMin
                                :opintojenLaajuusNumeroMax
                                :opintojenLaajuusyksikkoKoodiUri])))))

(defn- tuleva-jarjestaja-search-terms
  [hierarkia koulutus oppilaitos]
  (let [tarjoajat (organisaatio-tool/filter-indexable-for-hierarkia hierarkia (:tarjoajat koulutus))]
    (search-tool/search-terms
     :koulutus koulutus
     :tarjoajat tarjoajat
     :oppilaitos oppilaitos
     :toteutus-organisaationimi (remove nil? (distinct (map :nimi (flatten tarjoajat))))
     :koulutustyypit (search-tool/deduce-koulutustyypit koulutus oppilaitos)
     :kuva (:logo oppilaitos)
     :nimi (:nimi oppilaitos)
     :onkoTuleva true
     :metadata {:oppilaitosTila (:tila oppilaitos)
                :koulutustyyppi (search-tool/koulutustyyppi-for-organisaatio oppilaitos)})))

(defn assoc-jarjestaja-search-terms
  [koulutus toteutukset hakutiedot oppilaitokset execution-id]
  (if (seq (:tarjoajat koulutus))
    (let [search-terms (for [oppilaitos-hierarkia oppilaitokset
                             :let [oppilaitos (get-oppilaitos oppilaitos-hierarkia execution-id)]]
                         (if (tuleva-jarjestaja? oppilaitos-hierarkia toteutukset)
                           {:search_terms [(tuleva-jarjestaja-search-terms
                                             oppilaitos-hierarkia
                                             koulutus
                                             oppilaitos)]}
                           {:search_terms (jarjestaja-search-terms
                                            oppilaitos-hierarkia
                                            koulutus
                                            toteutukset
                                            hakutiedot
                                            oppilaitos)}))]
      (->> search-terms
           (apply merge-with concat)
           (merge koulutus)))
    (assoc koulutus :search_terms [(tuleva-jarjestaja-search-terms {} koulutus execution-id)])))

(defn- create-entry
  [koulutus]
  (let [entry (-> koulutus
                  (assoc :oid (:oid koulutus))
                  (assoc :nimi (get-esitysnimi koulutus))
                  (assoc :nimi_sort (common/create-sort-names (:nimi koulutus)))
                  (assoc :kielivalinta (:kielivalinta koulutus))
                  (dissoc :johtaaTutkintoon :esikatselu :modified :muokkaaja :_enrichedData :externalId :julkinen :tila :metadata :tarjoajat :sorakuvausId :organisaatioOid :ePerusteId)
                  (assoc :eperuste                (:ePerusteId koulutus))
                  (assoc :osaamismerkki           (get-in koulutus [:metadata :osaamismerkkiKoodiUri]))
                  (assoc :koulutukset             (:koulutuksetKoodiUri koulutus))
                  (assoc :tutkintonimikkeet       (search-tool/tutkintonimike-koodi-urit koulutus))
                  (assoc :kuvaus                  (get-in koulutus [:metadata :kuvaus]))
                  (assoc :teemakuva               (:teemakuva koulutus))
                  (assoc :koulutustyyppi          (:koulutustyyppi koulutus))
                  (assoc :isAvoinKorkeakoulutus   (get-in koulutus [:metadata :isAvoinKorkeakoulutus]))
                  (assoc :luokittelutermit        (get-in koulutus [:metadata :luokittelutermit]))
                  (assoc :opintojenLaajuus        (search-tool/opintojen-laajuus-koodi-uri koulutus))
                  (assoc :opintojenLaajuusNumero  (search-tool/opintojen-laajuus-numero koulutus))
                  (assoc :opintojenLaajuusNumeroMin (search-tool/opintojen-laajuus-numero-min koulutus))
                  (assoc :opintojenLaajuusNumeroMax (search-tool/opintojen-laajuus-numero-max koulutus))
                  (assoc :opintojenLaajuusyksikko (search-tool/opintojen-laajuusyksikko-koodi-uri koulutus))
                  (common/decorate-koodi-uris)
                  (assoc :search_terms (:search_terms koulutus))
                  (common/localize-dates))]
    (cond-> entry
      (amm-tutkinnon-osa? koulutus) (assoc :tutkinnonOsat (-> koulutus (search-tool/tutkinnon-osat) (common/decorate-koodi-uris)))
      (amm-osaamisala? koulutus)    (merge (common/decorate-koodi-uris {:osaamisalaKoodiUri (-> koulutus (search-tool/osaamisala-koodi-uri))})))))

(defn- assoc-toteutusten-tarjoajat
  [koulutus toteutukset]
  (let [tarjoajat (distinct (mapcat (fn [toteutus]
                                      (let [tarjoaja-oids (:tarjoajat toteutus)]
                                        (map (fn [tarjoaja-oid]
                                               (cache/find-oppilaitos-by-own-or-child-oid tarjoaja-oid)) tarjoaja-oids)))
                                    toteutukset))]
    (assoc koulutus
           :toteutustenTarjoajat
           {:count (count tarjoajat)
            :nimi (when-let [tarjoaja (first tarjoajat)] (get-in tarjoaja [:nimi]))})))

(defn create-index-entry
  [oid execution-id]
  (let [koulutus (kouta-backend/get-koulutus-with-cache oid execution-id)]
    (if (julkaistu? koulutus)
      (let [toteutukset (seq (kouta-backend/get-toteutus-list-for-koulutus-with-cache (:oid koulutus) true execution-id))
            hakutiedot (when toteutukset (kouta-backend/get-hakutiedot-for-koulutus-with-cache (:oid koulutus) execution-id))
            oppilaitos-hierarkia (find-hierarkiat-for-indexable-oppilaitokset (:tarjoajat koulutus))]
        (indexable/->index-entry oid (-> koulutus
                                         (assoc-jarjestaja-search-terms toteutukset hakutiedot oppilaitos-hierarkia execution-id)
                                         (assoc-toteutusten-tarjoajat toteutukset)
                                         (create-entry))))
      (indexable/->delete-entry oid))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
