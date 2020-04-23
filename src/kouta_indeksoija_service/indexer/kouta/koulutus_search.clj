(ns kouta-indeksoija-service.indexer.kouta.koulutus-search
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.rest.koodisto :refer [get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as organisaatio-tool]
            [kouta-indeksoija-service.indexer.tools.hakuaika :refer [->real-hakuajat]]
            [kouta-indeksoija-service.indexer.tools.general :refer :all]
            [kouta-indeksoija-service.indexer.tools.search :refer :all]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec]]))

(def index-name "koulutus-kouta-search")

(defn tuleva-jarjestaja-hit
  [hierarkia koulutus]
  (let [oppilaitos (organisaatio-tool/find-oppilaitos-from-hierarkia hierarkia)
        tarjoajat  (organisaatio-tool/filter-indexable-for-hierarkia hierarkia (:tarjoajat koulutus))]
    (hit :koulutustyyppi     (:koulutustyyppi koulutus)
         :koulutustyyppiUrit (koulutustyyppiKoodiUrit koulutus)
         :tarjoajat          tarjoajat
         :oppilaitos         oppilaitos
         :koulutusalaUrit    (koulutusalaKoodiUrit koulutus)
         :tutkintonimikeUrit (tutkintonimikeKoodiUrit koulutus)
         :nimet              (vector (:nimi koulutus))
         :oppilaitosOid      (:oid oppilaitos)
         :onkoTuleva         true
         :nimi               (:nimi oppilaitos)
         :metadata           {:koulutustyyppi (koulutustyyppi-for-organisaatio oppilaitos)})))

(defn jarjestaja-hits
  [hierarkia koulutus toteutukset]
    (let [oppilaitos (organisaatio-tool/find-oppilaitos-from-hierarkia hierarkia)]
      (vec (for [toteutus (->> toteutukset
                               (map (fn [t] (->> (:tarjoajat t)
                                                 (organisaatio-tool/filter-indexable-for-hierarkia hierarkia)
                                                 (assoc t :tarjoajat))))
                               (filter #(seq (:tarjoajat %))))
                 :let [opetus (get-in toteutus [:metadata :opetus])]]

             (hit :koulutustyyppi     (:koulutustyyppi koulutus)
                  :koulutustyyppiUrit (koulutustyyppiKoodiUrit koulutus)
                  :opetuskieliUrit    (:opetuskieliKoodiUrit opetus)
                  :tarjoajat          (:tarjoajat toteutus)
                  :oppilaitos         oppilaitos
                  :koulutusalaUrit    (koulutusalaKoodiUrit koulutus)
                  :tutkintonimikeUrit (tutkintonimikeKoodiUrit koulutus)
                  :nimet              (vector (:nimi koulutus) (:nimi toteutus))
                  ;:hakuOnKaynnissa   (->real-hakuajat hakutieto) TODO
                  ;:haut              (:haut hakutieto) TODO
                  ;:logo              TODO
                  :asiasanat          (asiasana->lng-value-map (get-in toteutus [:metadata :asiasanat]))
                  :ammattinimikkeet   (asiasana->lng-value-map (get-in toteutus [:metadata :ammattinimikkeet]))
                  :toteutusOid        (:oid toteutus)
                  :onkoTuleva         false
                  :nimi               (:nimi oppilaitos)
                  :metadata           {:tutkintonimikkeetKoodiUrit (tutkintonimikeKoodiUrit koulutus)
                                       :opetusajatKoodiUrit        (:opetusaikaKoodiUrit opetus)
                                       :onkoMaksullinen            (:onkoMaksullinen opetus)
                                       :maksunMaara                (:maksunMaara opetus)
                                       :koulutustyyppi             (koulutustyyppi-for-organisaatio oppilaitos)})))))

(defn tuleva-jarjestaja?
  [hierarkia toteutukset]
  (-> (organisaatio-tool/filter-indexable-for-hierarkia hierarkia (mapcat :tarjoajat toteutukset))
      (->distinct-vec)
      (empty?)))

;TODO
; (defn get-toteutuksen-hakutieto
;  [hakutiedot t]
;  (first (filter (fn [x] (= (:toteutusOid x) (:oid t))) hakutiedot)))

(defn find-indexable-oppilaitos-oids
  [tarjoajat]
  (->> tarjoajat
       (map cache/get-hierarkia)
       (map organisaatio-tool/find-oppilaitos-from-hierarkia)
       (remove nil?)
       (filter organisaatio-tool/indexable?)
       (map :oid)
       (->distinct-vec)))

(defn assoc-jarjestaja-hits
  [koulutus]
  (if (seq (:tarjoajat koulutus))
    (let [toteutukset (seq (kouta-backend/get-toteutus-list-for-koulutus (:oid koulutus) true))
          ;hakutiedot (when toteutukset (kouta-backend/get-hakutiedot-for-koulutus oid)) TODO
          ]
      (->> (for [hierarkia (map cache/get-hierarkia (find-indexable-oppilaitos-oids (:tarjoajat koulutus)))]
             (if (tuleva-jarjestaja? hierarkia toteutukset)
               {:hits [(tuleva-jarjestaja-hit hierarkia koulutus)]}
               {:hits (jarjestaja-hits hierarkia koulutus toteutukset)}))
           (apply merge-with concat)
           (merge koulutus)))
    (assoc koulutus :hits [(tuleva-jarjestaja-hit {} koulutus)])))

(defn- create-entry
  [koulutus]
  (-> koulutus
     (select-keys [:oid :nimi :kielivalinta])
     (assoc :eperuste                (:ePerusteId koulutus))
     (assoc :koulutus                (:koulutusKoodiUri koulutus))
     (assoc :tutkintonimikkeet       (tutkintonimikeKoodiUrit koulutus))
     (assoc :kuvaus                  (get-in koulutus [:metadata :kuvaus]))
     (assoc :teemakuva               (:teemakuva koulutus))
     (assoc :koulutustyyppi          (:koulutustyyppi koulutus))
     (assoc :opintojenlaajuus        (opintojenlaajuusKoodiUri koulutus))
     (assoc :opintojenlaajuusyksikko (opintojenlaajuusyksikkoKoodiUri koulutus))
     (common/decorate-koodi-uris)
     (assoc :hits (:hits koulutus))))

(defn create-index-entry
  [oid]
  (let [koulutus (kouta-backend/get-koulutus oid)]
    (if (julkaistu? koulutus)
      (indexable/->index-entry oid (-> koulutus
                                       (assoc-jarjestaja-hits)
                                       (create-entry)))
      (indexable/->delete-entry oid))))

(defn do-index
  [oids]
  (indexable/do-index index-name oids create-index-entry))

(defn get
  [oid]
  (indexable/get index-name oid))
