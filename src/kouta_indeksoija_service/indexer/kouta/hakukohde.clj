(ns kouta-indeksoija-service.indexer.kouta.hakukohde
  (:require [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.tools.general :refer [Tallennettu korkeakoulutus? get-non-korkeakoulu-koodi-uri]]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.tools.koodisto :as koodisto]))

(def index-name "hakukohde-kouta")
(defonce erityisopetus-koulutustyyppi "koulutustyyppi_4")

(defn- assoc-valintaperuste
  [hakukohde valintaperuste]
  (cond-> (dissoc hakukohde :valintaperusteId)
          (some? (:valintaperusteId hakukohde)) (assoc :valintaperuste (-> valintaperuste
                                                                           (dissoc :metadata)
                                                                           (common/complete-entry)))))

(defn- assoc-toteutus
  [hakukohde toteutus]
  (assoc hakukohde :toteutus (-> toteutus
                                 (common/complete-entry)
                                 (common/toteutus->list-item))))

(defn- assoc-sora-data
  [hakukohde sora-tiedot]
    (assoc
      hakukohde
      :sora
      (when sora-tiedot
        (select-keys sora-tiedot [:tila]))))

(defn- luonnos?
  [haku-tai-hakukohde]
  (= Tallennettu (:tila haku-tai-hakukohde)))

(defn- johtaa-tutkintoon?
  [koulutus]
  (:johtaaTutkintoon koulutus))

(defn- alkamiskausi-kevat?
  [haku hakukohde]
  (if-some [alkamiskausi-koodi-uri (if (:kaytetaanHaunAlkamiskautta hakukohde)
                                 (get-in haku [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamiskausiKoodiUri])
                                 (:alkamiskausiKoodiUri hakukohde))]
    (clojure.string/starts-with? alkamiskausi-koodi-uri "kausi_k#")
    false))

(defn- alkamisvuosi
  [haku hakukohde]
  (when-some [alkamisvuosi (if (:kaytetaanHaunAlkamiskautta hakukohde)
                       (get-in haku [:metadata :koulutuksenAlkamiskausi :koulutuksenAlkamisvuosi])
                       (:alkamisvuosi hakukohde))]
    (Integer/valueOf alkamisvuosi)))

(defn- alkamiskausi-ennen-syksya-2016?
  [haku hakukohde]
  (if-some [alkamisvuosi (alkamisvuosi haku hakukohde)]
    (or (< alkamisvuosi 2016)
        (and (= alkamisvuosi 2016)
             (alkamiskausi-kevat? haku hakukohde)))
    false))

(defn- some-kohdejoukon-tarkenne?
  [haku]
  (not (clojure.string/blank? (:kohdejoukonTarkenneKoodiUri haku))))

(defn- jatkotutkintohaku-tarkenne?
  [haku]
  (clojure.string/starts-with?
   (:kohdejoukonTarkenneKoodiUri haku)
   "haunkohdejoukontarkenne_3#"))

(defn- ->ei-yps
  [syy]
  {:voimassa false :syy syy})

(def ^:private yps
  {:voimassa true
   :syy      "Hakukohde on yhden paikan säännön piirissä"})

(defn- assoc-yps
  [hakukohde haku koulutus]
  (assoc
   hakukohde
   :yhdenPaikanSaanto
   (cond (luonnos? haku)
         (->ei-yps "Haku on luonnos tilassa")

         (luonnos? hakukohde)
         (->ei-yps "Hakukohde on luonnos tilassa")

         (not (korkeakoulutus? koulutus))
         (->ei-yps "Ei korkeakoulutus koulutusta")

         (not (johtaa-tutkintoon? koulutus))
         (->ei-yps "Ei tutkintoon johtavaa koulutusta")

         (alkamiskausi-ennen-syksya-2016? haku hakukohde)
         (->ei-yps "Koulutuksen alkamiskausi on ennen syksyä 2016")

         (and (some-kohdejoukon-tarkenne? haku)
              (not (jatkotutkintohaku-tarkenne? haku)))
         (->ei-yps (str "Haun kohdejoukon tarkenne on "
                        (:kohdejoukonTarkenneKoodiUri haku)))

         :else
         yps)))

(defn- assoc-hakulomake-linkki
  [hakukohde haku]
  (let [link-holder (if (true? (:kaytetaanHaunHakulomaketta hakukohde)) haku hakukohde)]
    (conj hakukohde (common/create-hakulomake-linkki-for-hakukohde link-holder (:oid hakukohde)))))

(defn- conj-er-koulutus [toteutus koulutustyypit]
  (if (and
       (true? (get-in toteutus [:metadata :ammatillinenPerustutkintoErityisopetuksena]))
       (not (.contains koulutustyypit erityisopetus-koulutustyyppi)))
    (conj koulutustyypit erityisopetus-koulutustyyppi)
    koulutustyypit))

(defn- assoc-koulutustyypit
  [hakukohde toteutus koulutus]
  (->> koulutus
       :koulutuksetKoodiUri
       (mapcat koodisto/koulutustyypit)
       (map :koodiUri)
       (conj-er-koulutus toteutus)
       (assoc hakukohde :koulutustyypit)))

(defn- assoc-onko-harkinnanvarainen-koulutus
  [hakukohde koulutus]
  (let [non-korkeakoulu-koodi-uri (get-non-korkeakoulu-koodi-uri koulutus)]
    (assoc hakukohde :onkoHarkinnanvarainenKoulutus (and
                                                      (some? non-korkeakoulu-koodi-uri)
                                                      (nil? (koodisto/ei-harkinnanvaraisuutta non-korkeakoulu-koodi-uri))))))

(defn create-index-entry
  [oid]
  (let [hakukohde      (kouta-backend/get-hakukohde oid)
        haku           (kouta-backend/get-haku (:hakuOid hakukohde))
        toteutus       (kouta-backend/get-toteutus (:toteutusOid hakukohde))
        koulutus       (kouta-backend/get-koulutus (:koulutusOid toteutus))
        sora-kuvaus    (kouta-backend/get-sorakuvaus (:sorakuvausId koulutus))
        valintaperusteId (:valintaperusteId hakukohde)
        valintaperuste (when-not
                         (clojure.string/blank? valintaperusteId)
                         (kouta-backend/get-valintaperuste valintaperusteId))]
    (indexable/->index-entry oid
                             (-> hakukohde
                                 (assoc-yps haku koulutus)
                                 (common/complete-entry)
                                 (assoc-sora-data sora-kuvaus)
                                 (assoc-onko-harkinnanvarainen-koulutus koulutus)
                                 (assoc-koulutustyypit toteutus koulutus)
                                 (assoc-toteutus toteutus)
                                 (assoc-valintaperuste valintaperuste)
                                 (assoc-hakulomake-linkki haku)))))

(defn do-index
  [oids]
  (indexable/do-index index-name oids create-index-entry))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
