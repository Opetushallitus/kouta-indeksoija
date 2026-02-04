(ns kouta-indeksoija-service.fixture.kouta-indexer-fixture
  (:require [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.indexer.indexer :as indexer]
            [kouta-indeksoija-service.elastic.tools :as tools]
            [kouta-indeksoija-service.fixture.external-services :refer :all]
            [kouta-indeksoija-service.indexer.cache.hierarkia :refer [clear-all-cached-data]]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.indexer.tools.general :refer [not-arkistoitu? not-poistettu?]]
            [kouta-indeksoija-service.test-tools :refer [parse debug-pretty]]
            [clojure.test :refer :all]
            [cheshire.core :refer [parse-string, generate-string]]
            [clojure.string :as str]
            [clj-elasticsearch.elastic-utils :as u]
            [clojure.walk :refer [keywordize-keys stringify-keys postwalk]]
            [clj-test-utils.elasticsearch-docker-utils :refer [start-elasticsearch stop-elasticsearch]])
  (:import (java.time LocalDate)))

(defn reload-kouta-indexer-fixture [f]
  (require 'kouta-indeksoija-service.fixture.kouta-indexer-fixture :reload)
  (f))

(defn restart-elasticsearch [tests]
  (stop-elasticsearch)
  (start-elasticsearch)
  (tests))

(defonce koulutukset (atom {}))
(defonce toteutukset (atom {}))
(defonce haut (atom {}))
(defonce hakukohteet (atom {}))
(defonce valintaperusteet (atom {}))
(defonce sorakuvaukset (atom {}))
(defonce oppilaitokset (atom {}))
(defonce oppilaitoksen-osat (atom {}))

(defn complete-kielistetyt
  [e]
  (if (and (:kielivalinta e) (vector? (:kielivalinta e)))
    (let [kielet (:kielivalinta e)
          kieli-value (fn [kieli nimi] (assoc {} (keyword kieli) (str nimi " " kieli)))
          do-kielista (fn [curr k] (assoc curr k (into {} (map (fn [kieli] (kieli-value kieli (get e k))) kielet))))
          kielista (fn [curr k] (if (or (nil? (get curr k)) (map? (get curr k))) curr (do-kielista curr k)))]
      (-> e
          (kielista :nimi)
          (kielista :esitysnimi)
          (kielista :hakulomakeKuvaus)
          (kielista :pohjakoulutusvaatimusTarkenne)
          (kielista :hakulomakeLinkki)))
    e))

(defn common-start-time
  []
  (let [day (-> (LocalDate/now) (.plusDays 1) (.toString))]
    (str day "T09:49")))

(def far-enough-in-the-future-start-time "2042-03-24T09:49")

(def far-enough-in-the-future-end-time "2042-03-29T09:49")

(defn common-end-time
  []
  (let [day (-> (LocalDate/now) (.plusDays 1) (.toString))]
    (str day "T09:58")))

(defn common-near-future-time
  []
  (let [day (-> (LocalDate/now) (.plusDays 3) (.toString))]
    (str day "T09:58")))

(defn fix-default-format
  [entity]
  (let [->vector (fn [e k] (if (get e k) (if (vector? (get e k)) e (assoc e k (vec (map str/trim (str/split (get e k) #","))))) e))
        complete-esitysnimi (fn [e] (if (:esitysnimi e)
                                      (-> e
                                          (assoc :_enrichedData {:esitysnimi (:esitysnimi e)})
                                          (dissoc :esitysnimi))
                                      e))
        fix-organisaatio (fn [e] (if (and (:organisaatio e) (string? (:organisaatio e)))
                                   (-> e
                                       (assoc  :organisaatioOid (:organisaatio e))
                                       (dissoc :organisaatio))
                                   e))
        fix-externalid (fn [e] (if (and (not (nil? (:externalId e))) (not (string? (:externalId e))))
                                 (assoc e :externalId (str (:externalId e))) e))
        fix-alkamisvuosi (fn [e] (postwalk (fn [sub] (if (:koulutuksenAlkamisvuosi sub)
                                                       (assoc sub :koulutuksenAlkamisvuosi
                                                              (-> (LocalDate/now)
                                                                  (.getYear)
                                                                  (.toString))) sub)) e))]
    (-> entity
        (->vector :kielivalinta)
        (->vector :tarjoajat)
        (->vector :koulutuksetKoodiUri)
        (->vector :pohjakoulutusvaatimusKoodiUrit)
        (complete-kielistetyt)
        (complete-esitysnimi)
        (fix-organisaatio)
        (fix-externalid)
        (fix-alkamisvuosi))))

(defn ->keywordized-json
  [string]
  (fix-default-format (keywordize-keys (parse-string string))))

(defn json->clj-map
  [string]
  (let [clj-map (->keywordized-json string)]
    (reduce-kv (fn [m k v]
                 (assoc m k (if (string? v) v (generate-string v)))) {} clj-map)))

(defn ->java-map
  [clj-map]
  (java.util.HashMap. (stringify-keys clj-map)))

(defn ->clj-map
  [java-map]
  (keywordize-keys (merge {} java-map)))

(defn java-map->pretty-json [java-map]
  (let [clj-map (->clj-map java-map)
        complete-clj-map  (reduce-kv (fn [m k v]
                                       (assoc m k (if (string? v)
                                                    (try
                                                      (->keywordized-json v)
                                                      (catch Exception _ v))
                                                    v))) {} clj-map)]
    (generate-string complete-clj-map {:pretty true})))

(defn visible
  [e]
  (and (not-arkistoitu? e) (not-poistettu? e)))

(def default-koulutus-map (->keywordized-json (slurp "test/resources/kouta/default-koulutus.json")))
(def default-toteutus-map (->keywordized-json (slurp "test/resources/kouta/default-toteutus.json")))
(def default-haku-map (->keywordized-json (slurp "test/resources/kouta/default-haku.json")))
(def default-hakukohde-map (->keywordized-json (slurp "test/resources/kouta/default-hakukohde.json")))
(def default-valintaperuste-map (->keywordized-json (slurp "test/resources/kouta/default-valintaperuste.json")))
(def default-sorakuvaus-map (->keywordized-json (slurp "test/resources/kouta/default-sorakuvaus.json")))
(def default-oppilaitos-map (merge (->keywordized-json (slurp "test/resources/kouta/default-oppilaitos.json"))
                                   {:_enrichedData {:organisaatio (->keywordized-json (slurp "test/resources/kouta/default-kouta-organisaatio.json"))}}))
(def default-oppilaitoksen-osa-map (->keywordized-json (slurp "test/resources/kouta/default-oppilaitoksen-osa.json")))

(def lk-toteutus-metadata (->keywordized-json (slurp "test/resources/kouta/lk-toteutus-metadata.json")))
(def amm-tutkinnon-osa-toteutus-metadata (->keywordized-json (slurp "test/resources/kouta/amm-tutkinnon-osa-toteutus-metadata.json")))
(def tpo-toteutus-metadata (->keywordized-json (slurp "test/resources/kouta/taiteen-perusopetus-toteutus-metadata.json")))
(def muu-toteutus-metadata (->keywordized-json (slurp "test/resources/kouta/muu-toteutus-metadata.json")))

(defonce koulutus-metatieto
  {:tyyppi "amm"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso1_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso1_02#1"]
   :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}
   :lisatiedot [{:otsikkoKoodiUri "koulutuksenlisatiedot_03#1"
                 :teksti {:fi "Opintojen lisätieto"
                          :sv "Opintojen lisätieto sv"}}]})

(defonce oletus-toteutus-metatieto
  {:tyyppi           "amm"
   :asiasanat        [{:kieli "fi" :arvo "hevonen"}]
   :ammattinimikkeet [{:kieli "fi" :arvo "ponityttö"}]
   :ammatillinenPerustutkintoErityisopetuksena false
   :opetus {:opetuskieliKoodiUrit ["oppilaitoksenopetuskieli_02"]
            :opetustapaKoodiUrit ["opetuspaikkakk_02"]
            :opetusaikaKoodiUrit []}})

(defonce aikuisten-perusopetus-toteutus-metatieto
  {:tyyppi           "aikuisten-perusopetus"
   :asiasanat        [{:kieli "fi" :arvo "hevonen"}]
   :ammattinimikkeet [{:kieli "fi" :arvo "ponityttö"}]
   :ammatillinenPerustutkintoErityisopetuksena false
   :opetus {:opetuskieliKoodiUrit ["oppilaitoksenopetuskieli_02"]
            :opetustapaKoodiUrit ["opetuspaikkakk_02"]
            :opetusaikaKoodiUrit []}})

(defonce amm-toteutus-metatieto
  (merge (:metadata default-toteutus-map)
         {:tyyppi           "amm"
          :asiasanat        [{:kieli "fi" :arvo "traktori"}]
          :ammattinimikkeet [{:kieli "fi" :arvo "korjaaja"}]
          :ammatillinenPerustutkintoErityisopetuksena false
          :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}}))

(defonce amk-toteutus-metatieto
  {:tyyppi           "amk"
   :asiasanat        [{:kieli "fi" :arvo "hevonen"}]
   :ammattinimikkeet [{:kieli "fi" :arvo "ponipoika"}]
   :opetus {:opetuskieliKoodiUrit ["oppilaitoksenopetuskieli_01"]
            :opetustapaKoodiUrit ["opetuspaikkakk_01"]
            :koulutuksenTarkkaAlkamisaika true
            :koulutuksenAlkamisvuosi 2019
            :suunniteltuKestoVuodet 3
            :suunniteltuKestoKuukaudet 4
            :maksullisuustyyppi "lukuvuosimaksu"}})

(defonce amm-osaamisala-toteutus-metatieto
  {:tyyppi "amm-osaamisala"
   :opetus {:opetuskieliKoodiUrit ["oppilaitoksenopetuskieli_1", "oppilaitoksenopetuskieli_2"]
            :opetustapaKoodiUrit ["opetuspaikkakk_1", "opetuspaikkakk_2"]
            :opetusaikaKoodiUrit ["opetusaikakk_1"]
            :maksullisuustyyppi "lukuvuosimaksu"
            :maksunMaara 500
            :onkoApuraha false}})

(defonce maksuton-amm-opetus
  {:opetus {:maksullisuustyyppi "maksuton"
            :maksunMaara nil
            :maksullisuusKuvaus nil
            :apuraha nil}
   :onkoApuraha nil})

(defonce amm-erityisopetus-toteutus-metatieto
  (merge amm-toteutus-metatieto maksuton-amm-opetus {:ammatillinenPerustutkintoErityisopetuksena true}))

(defonce tuva-erityisopetus-toteutus-metatieto
  (merge amm-toteutus-metatieto maksuton-amm-opetus {:tyyppi "tuva" :jarjestetaanErityisopetuksena true}))

(defonce tuva-toteutus-metatieto
  (merge tuva-erityisopetus-toteutus-metatieto {:jarjestetaanErityisopetuksena false}))

(defonce yo-toteutus-metatieto
  (merge (dissoc (:metadata default-toteutus-map) :osaamisalat :ammatillinenPerustutkintoErityisopetuksena)
         {:tyyppi           "yo"
          :asiasanat        []
          :ammattinimikkeet []
          :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}}))

(defonce yo-koulutus-metadata
  {:tyyppi "yo"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumero 26
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]
   :kuvauksenNimi {:fi "kuvaus", :sv "kuvaus sv"}
   :kuvaus {}
   :lisatiedot []})

(defonce lk-koulutus-metadata
  {:tyyppi "lk"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]})

(defonce amk-koulutus-metadata
  {:tyyppi "amk"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumero 27
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]
   :tutkintonimikeKoodiUrit ["tutkintonimikekk_033#1" "tutkintonimikekk_031#1"]
   :kuvauksenNimi {:fi "kuvaus", :sv "kuvaus sv"}})

(defonce amm-tutkinnon-osa-enriched-data
  {:esitysnimi {:fi "Autoalan perustutkinto 0 fi (voimaantulo 6.6.2025)", :sv "Autoalan perustutkinto 0 sv (voimaantulo 6.6.2025)"}})

(defonce amm-tutkinnon-osa-koulutus-metadata
  {:tyyppi "amm-tutkinnon-osa"
   :tutkinnonOsat [{:koulutusKoodiUri "koulutus_123123#1" :tutkinnonosaId 1234 :tutkinnonosaViite 5678}
                   {:koulutusKoodiUri "koulutus_123125#1" :tutkinnonosaId 1235 :tutkinnonosaViite 5677}
                   {:koulutusKoodiUri "koulutus_123444#1" :tutkinnonosaId 1236 :tutkinnonosaViite 5679}
                   {:ePerusteId 123 :koulutusKoodiUri "koulutus_371101#1" :tutkinnonosaId 1237 :tutkinnonosaViite 5680}]
   :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}})

(defonce amm-osaamisala-koulutus-metadata
  {:tyyppi "amm-osaamisala"
   :osaamisalaKoodiUri "osaamisala_01"
   :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}})

(defonce amm-muu-koulutus-metadata
  {:tyyppi "amm-muu"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_4#1"
   :opintojenLaajuusNumero 11})

(defonce lukio-koulutus-metadata
  {:tyyppi "lk"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso1_001#1"]
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumero 25
   :kuvauksenNimi {:fi "kuvaus", :sv "kuvaus sv"}
   :kuvaus {}
   :lisatiedot []})

(defonce tuva-koulutus-metadata
  {:tyyppi "tuva"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_8#1"
   :opintojenLaajuusNumero 38
   :linkkiEPerusteisiin {:fi "http://testilinkki.fi" :sv "http://testilinkki.fi/sv"}
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}})

(defonce telma-koulutus-metadata
  {:tyyppi "telma"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_6#1"
   :opintojenLaajuusNumero 41
   :linkkiEPerusteisiin {:fi "http://testilinkki.fi" :sv "http://testilinkki.fi/sv"}
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}})

(defonce vapaa-sivistystyo-muu-metadata
  {:tyyppi "vapaa-sivistystyo-muu"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_080#1"]
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_8#1"
   :opintojenLaajuusNumero 38
   :linkkiEPerusteisiin {:fi "http://testilinkki.fi" :sv "http://testilinkki.fi/sv"}
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}})

(defonce aikuisten-perusopetus-koulutus-metadata
  {:tyyppi "aikuisten-perusopetus"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumero 13
   :linkkiEPerusteisiin {:fi "http://testilinkki.fi" :sv "http://testilinkki.fi/sv"}
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}
   :lisatiedot []})

(defonce kk-opintojakso-koulutus-metadata
  {:tyyppi "kk-opintojakso"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumeroMin 14
   :opintojenLaajuusNumeroMax 15
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}
   :lisatiedot []})

(defonce kk-opintokokonaisuus-koulutus-metadata
  (merge kk-opintojakso-koulutus-metadata {:tyyppi "kk-opintokokonaisuus" :opintojenLaajuusNumeroMin 24 :opintojenLaajuusNumeroMax 25}))

(defonce erikoistumiskoulutus-metadata
  {:tyyppi "erikoistumiskoulutus"
   :erikoistumiskoulutusKoodiUri "erikoistumiskoulutukset_001#2"
   :koulutusalaKoodiUrit ["kansallinenkoulutusluokitus2016koulutusalataso2_01#1"
                          "kansallinenkoulutusluokitus2016koulutusalataso2_02#1"]
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_2#1"
   :opintojenLaajuusNumeroMin 5
   :opintojenLaajuusNumeroMax 10
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}
   :lisatiedot []})

(defonce tpo-koulutus-metadata
  {:tyyppi "taiteen-perusopetus"
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}
   :linkkiEPerusteisiin {:fi "http://testilinkki.fi" :sv "http://testilinkki.fi/sv"}
   :lisatiedot []})

(defonce osaamismerkki-koulutus-metadata
  {:tyyppi "vapaa-sivistystyo-osaamismerkki"
   :kuvaus {:fi "kuvausteksti" :sv "kuvausteksti sv"}
   :osaamismerkkiKoodiUri "osaamismerkit_1022#2"
   :opintojenLaajuusyksikkoKoodiUri "opintojenlaajuusyksikko_4"
   :opintojenLaajuusNumero 1})

(defonce osaamismerkki-toteutus-metatieto
  (merge (dissoc (:metadata default-toteutus-map) :osaamisalat :ammatillinenPerustutkintoErityisopetuksena)
         {:tyyppi           "vapaa-sivistystyo-osaamismerkki"
          :asiasanat        []
          :ammattinimikkeet []
          :kuvaus  {:fi "kuvaus", :sv "kuvaus sv"}}))

(defonce maksullinen-kk-haku-metadata
  {:yhteyshenkilot []
   :tulevaisuudenAikataulu []
   :koulutuksenAlkamiskausi {:alkamiskausityyppi "alkamiskausi ja -vuosi"
                             :henkilokohtaisenSuunnitelmanLisatiedot {}
                             :koulutuksenAlkamiskausiKoodiUri "kausi_s#1"
                             :koulutuksenAlkamisvuosi "2025"}
   :isMuokkaajaOphVirkailija true})

(defn add-koulutus-mock
  [oid & {:as params}]
  (let [koulutus (fix-default-format (merge default-koulutus-map {:oid oid :organisaatioOid oppilaitos-oid} params))]
    (swap! koulutukset assoc oid koulutus)))

(defn update-koulutus-mock
  [oid & {:as params}]
  (if (nil? params)
    (swap! koulutukset assoc oid nil)
    (let [koulutus (fix-default-format (merge (get @koulutukset oid) params))]
      (swap! koulutukset assoc oid koulutus))))

(defn mock-get-koulutus
  [oid execution-id]
  (get @koulutukset oid))

(defn get-organisaation-koulutukset-ja-toteutukset
  [koulutus-oids organisaatio-and-child-oids]
  (let [koulutus-oids-set (set koulutus-oids)
        koulutukset (filter
                     #(contains? koulutus-oids-set (:oid %))
                     (vals @koulutukset))
        koulutusten-toteutukset (filter
                                 #(contains? koulutus-oids-set (:koulutusOid %))
                                 (vals @toteutukset))
        oppilaitoksen-ja-toimipisteiden-toteutukset (filter
                                                     #(some (set organisaatio-and-child-oids) (:tarjoajat %))
                                                     koulutusten-toteutukset)]
    (into {} (for [koulutus koulutukset]
               [(:oid koulutus) (assoc koulutus
                                       :toteutukset (filter #(= (:oid koulutus) (:koulutusOid %))
                                                            oppilaitoksen-ja-toimipisteiden-toteutukset))]))))

(defn mock-get-koulutukset-by-tarjoaja
  [oid execution-id]
  (cond (= oid helsingin-yliopisto) (get-organisaation-koulutukset-ja-toteutukset [traktoriala-oid hevosala-oid] [helsingin-yliopisto helsingin-toimipiste])
        (= oid punkaharjun-yliopisto) (get-organisaation-koulutukset-ja-toteutukset
                                       [traktoriala-oid hevosala-oid hevostutkinnon-osa-oid hevososaamisala-oid yo-koulutus-oid amk-oid lukio-oid2 amm-muu-oid]
                                       [punkaharjun-yliopisto punkaharjun-toimipiste-1 punkaharjun-toimipiste-2])
        :else (let [oids #{oid, (str oid "1"), (str oid "2"), (str oid "3")}
                    pred (fn [e] (and (= (:tila e) "julkaistu") (some oids (:tarjoajat e))))]
                (into {}
                      (for [koulutus (let [tarjoajan-koulutukset (filter pred (vals @koulutukset))
                                           koulutus-with-toteutukset (for [koulutus tarjoajan-koulutukset]
                                                                       (assoc koulutus
                                                                              :toteutukset
                                                                              (filter #(and (= (:koulutusOid %) (:oid koulutus))
                                                                                            (or (= (:tila %) "julkaistu")
                                                                                                (= (:tila %) "tallennettu")))
                                                                                      (vals @toteutukset))))]
                                       koulutus-with-toteutukset)]
                        [(:oid koulutus) koulutus])))))

(defn mock-get-koulutus-oids-by-tarjoajat
  [tarjoaja-oids]
  (map #(:oid %)
       (filter
        #(some (set tarjoaja-oids) (:tarjoajat %))
        (vals @koulutukset))))

(defn mock-get-hakukohde-oids-by-jarjestyspaikat
  [oids execution-id]
  (let [pred (fn [hk] (some (fn [oid] (= oid (:jarjestyspaikkaOid hk))) oids))]
    (map :oid (filter pred (vals @hakukohteet)))))

(defn mock-get-toteutus-oids-by-tarjoajat
  [oids execution-id]
  (let [pred (fn [toteutus]
               (some
                (fn [oid]
                  (some
                   (fn [tarjoaja-oid]
                     (= oid tarjoaja-oid))
                   (:tarjoajat toteutus)))
                oids))]
    (map :oid (filter pred (vals @toteutukset)))))

(defn add-toteutus-mock
  [oid koulutusOid & {:as params}]
  (let [toteutus (fix-default-format (merge default-toteutus-map {:organisaatio oppilaitos-oid :koulutusOid koulutusOid :oid oid} params))]
    (swap! toteutukset assoc oid toteutus)))

(defn update-toteutus-mock
  [oid & {:as params}]
  (if (nil? params)
    (swap! toteutukset assoc oid nil)
    (let [toteutus (merge (get @toteutukset oid) params)]
      (swap! toteutukset assoc oid toteutus))))

(defn mock-get-toteutus
  [oid execution-id]
  (get @toteutukset oid))

(defn mock-get-toteutukset
  ([koulutusOid vainJulkaistut execution-id]
   (let [pred (fn [e] (and (= (:koulutusOid e) koulutusOid) (or (not vainJulkaistut) (= (:tila e) "julkaistu"))))]
     (filter pred (vals @toteutukset))))
  ([koulutusOid execution-id]
   (mock-get-toteutukset koulutusOid false execution-id)))

(defn set-vals-in-depth
  [e k v]
  (postwalk (fn [sub] (if (get sub k) (assoc sub k v) sub)) e))

;; Ellei hakuaikoja ole erikseen annettu -> käytetään oletuksia. Oletus-jsonista tulevia arvoja ei kayteta.
(defn add-haku-mock
  [oid & {:as params}]
  (let [fix-dates (fn [haku] (-> haku
                                 (set-vals-in-depth :alkaa (common-start-time))
                                 (set-vals-in-depth :paattyy (common-end-time))
                                 (assoc :hakuajat [{:alkaa (if (nil? (:hakuaikaAlkaa haku))
                                                             (common-start-time) (:hakuaikaAlkaa haku))
                                                    :paattyy (if (nil? (:hakuaikaPaattyy haku))
                                                               (common-end-time) (:hakuaikaPaattyy haku))}])
                                 (dissoc :hakuaikaAlkaa :hakuaikaPaattyy)
                                 (set-vals-in-depth :hakuaikaAlkaa (common-start-time))
                                 (set-vals-in-depth :hakuaikaPaattyy (common-end-time))
                                 (set-vals-in-depth :hakukohteenMuokkaamisenTakaraja (common-end-time))
                                 (set-vals-in-depth :hakukohteenLiittamisenTakaraja (common-start-time))
                                 (set-vals-in-depth :ajastettuJulkaisu (common-near-future-time))))
        haku (fix-default-format (fix-dates (merge (dissoc default-haku-map :hakuaikaAlkaa :hakuaikaPaattyy)
                                                   {:organisaatio oppilaitos-oid} {:oid oid} params)))]
    (swap! haut assoc oid haku)))

(defn update-haku-mock
  [oid & {:as params}]
  (if (nil? params)
    (swap! haut assoc oid nil)
    (let [haku (merge (get @haut oid) params)]
      (swap! haut assoc oid haku))))

(defn mock-get-pistehistoria [tarjoajaOid hakukohdekoodi lukiolinjakoodi execution-id]
  (if (nil? hakukohdekoodi) []
      (map #(-> %
                (assoc :tarjoaja tarjoajaOid)
                (assoc :hakukohdekoodi hakukohdekoodi))
           [{:vuosi "2022" :pisteet 6} {:vuosi "2021" :pisteet 8}])))

(defn mock-get-haku
  [oid execution-id]
  (get @haut oid))

;; Ellei hakuaikoja ole erikseen annettu -> kaytetaan oletuksia. Oletus-jsonista tulevia arvoja ei kayteta.
(defn add-hakukohde-mock
  [oid toteutusOid hakuOid & {:as params}]
  (let [fix-dates (fn [hk] (-> hk
                               (set-vals-in-depth :alkaa (common-start-time))
                               (set-vals-in-depth :paattyy (common-end-time))
                               (assoc :hakuajat [{:alkaa (if (nil? (:hakuaikaAlkaa hk))
                                                           (common-start-time) (:hakuaikaAlkaa hk))
                                                  :paattyy (if (nil? (:hakuaikaPaattyy hk))
                                                             (common-end-time) (:hakuaikaPaattyy hk))}])
                               (dissoc :hakuaikaAlkaa :hakuaikaPaattyy)
                               (set-vals-in-depth :koulutuksenAlkamispaivamaara far-enough-in-the-future-start-time)
                               (set-vals-in-depth :koulutuksenPaattymispaivamaara far-enough-in-the-future-end-time)
                               (set-vals-in-depth :toimitusaika (common-end-time))))
        fix-valintaperuste (fn [hk] (if (:valintaperuste hk) (assoc hk :valintaperusteId (:valintaperuste hk)) hk))
        fix-muu-pk-vaatimus (fn [hk] (if (nil? (:muuPohjakoulutusvaatimus hk)) (assoc hk :muuPohjakoulutusvaatimus {}) hk))

        hakukohde (fix-default-format
                   (fix-dates
                    (fix-valintaperuste
                     (fix-muu-pk-vaatimus
                      (merge (dissoc default-hakukohde-map :hakuaikaAlkaa :hakuaikaPaattyy)
                             {:organisaatio oppilaitos-oid}
                             params
                             {:oid oid :hakuOid hakuOid :toteutusOid toteutusOid}
                             {:liitteidenToimitusaika (common-near-future-time)})))))]
    (swap! hakukohteet assoc oid hakukohde)))

(defn update-hakukohde-mock
  [oid & {:as params}]
  (if (nil? params)
    (swap! hakukohteet assoc oid nil)
    (let [hakukohde (merge (get @hakukohteet oid) params)]
      (swap! hakukohteet assoc oid hakukohde))))

(defn mock-get-hakukohde
  [oid execution-id]
  (get @hakukohteet oid))

(defn add-valintaperuste-mock
  [id & {:as params}]
  (let [fix-in-depth (fn [e] (-> e
                                 (set-vals-in-depth :alkaa (common-start-time))
                                 (set-vals-in-depth :paattyy (common-end-time))))
        valintaperuste (fix-default-format
                        (fix-in-depth
                         (merge default-valintaperuste-map {:id id :organisaatio oppilaitos-oid} params)))]
    (swap! valintaperusteet assoc id valintaperuste)))

(defn update-valintaperuste-mock
  [id & {:as params}]
  (if (nil? params)
    (swap! valintaperusteet assoc id nil)
    (let [valintaperuste (merge (get @valintaperusteet id) params)]
      (swap! valintaperusteet assoc id valintaperuste))))

(defn mock-get-valintaperuste
  [id execution-id]
  (get @valintaperusteet id))

(defn add-sorakuvaus-mock
  [id & {:as params}]
  (let [strip-extra (fn [sk] (dissoc sk :sorakuvausId :julkinen))
        sorakuvaus (fix-default-format (strip-extra (merge default-sorakuvaus-map {:organisaatio oppilaitos-oid :id id} params)))]
    (swap! sorakuvaukset assoc id sorakuvaus)))

(defn update-sorakuvaus-mock
  [id & {:as params}]
  (if (nil? params)
    (swap! sorakuvaukset assoc id nil)
    (let [sorakuvaus (merge (get @sorakuvaukset id) params)]
      (swap! sorakuvaukset assoc id sorakuvaus))))

(defn add-oppilaitos-mock
  ([oid & {:as params}]
   (let [oppilaitos (fix-default-format (merge default-oppilaitos-map {:organisaatio oppilaitos-oid :oid oid} params))]
     (swap! oppilaitokset assoc oid oppilaitos)))
  ([oppilaitos]
   (let [oid (:oid oppilaitos)]
     (swap! oppilaitokset assoc oid (fix-default-format
                                     (merge
                                      oppilaitos
                                      {:organisaatio oppilaitos-oid :oid oid}))))))

(defn add-oppilaitos-mock-without-kouta-oppilaitos
  [oid organisaatio-data]
  (swap! oppilaitokset assoc oid (fix-default-format organisaatio-data)))

(defn update-oppilaitos-mock
  [oid & {:as params}]
  (let [oppilaitos (merge (get @oppilaitokset oid) params)]
    (swap! oppilaitokset assoc oid oppilaitos)))

(defn mock-get-oppilaitos
  ([oid _ _]
   (get @oppilaitokset oid))
  ([oid _]
   (get @oppilaitokset oid)))

(defn add-oppilaitoksen-osa-mock
  ([oid oppilaitosOid & {:as params}]
   (let [oppilaitoksen-osa (fix-default-format (merge default-oppilaitoksen-osa-map {:organisaatio oppilaitos-oid :oppilaitosOid oppilaitosOid :oid oid} params))]
     (swap! oppilaitoksen-osat assoc oid oppilaitoksen-osa)))
  ([oppilaitoksen-osa]
   (swap! oppilaitokset assoc (:oid oppilaitoksen-osa) (fix-default-format oppilaitoksen-osa))))

(defn update-oppilaitoksen-osa-mock
  [oid & {:as params}]
  (let [oppilaitoksen-osa (merge (get @oppilaitoksen-osat oid) params)]
    (swap! oppilaitoksen-osat assoc oid oppilaitoksen-osa)))

(defn mock-get-oppilaitoksen-osa
  [oid]
  (get @oppilaitoksen-osat oid))

(defn mock-get-sorakuvaus
  [id execution-id]
  (get @sorakuvaukset id))

(defn mock-get-hakukohteet-by-haku
  [hakuOid execution-id]
  (let [pred (fn [e] (= (:hakuOid e) hakuOid))
        ->list-item (fn [hk] (into {}
                                   (remove (comp nil? second)
                                           (assoc {}
                                                  :oid (:oid hk)
                                                  :toteutusOid (:toteutusOid hk)
                                                  :hakuOid (:hakuOid hk)
                                                  :valintaperusteId (:valintaperusteId hk)
                                                  :nimi (:nimi hk)
                                                  :hakukohdeKoodiUri (:hakukohdeKoodiUri hk)
                                                  :tila (:tila hk)
                                                  :jarjestyspaikkaOid (:jarjestyspaikkaOid hk)
                                                  :organisaatioOid (:organisaatioOid hk)
                                                  :muokkaaja (:muokkaaja hk)
                                                  :modified (:modified hk)
                                                  :_enrichedData (:_enrichedData hk)))))]
    (map ->list-item (filter pred (vals @hakukohteet)))))

(defn mock-list-haut-by-toteutus
  [toteutusOid execution-id]
  (let [find-hakukohteet (fn [tOid] (filter (fn [hk] (and (visible hk) (= (:toteutusOid hk) tOid))) (vals @hakukohteet)))
        find-haut (fn [hakuOids] (filter visible (map #(mock-get-haku % (System/currentTimeMillis)) hakuOids)))]
    (find-haut (map :hakuOid (find-hakukohteet toteutusOid)))))

(defn mock-list-hakukohteet-by-valintaperuste
  [valintaperusteId execution-id]
  (filter (fn [hk] (= (:valintaperusteId hk) valintaperusteId)) (vals @hakukohteet)))

(defn mock-list-toteutukset-by-haku
  [hakuOid execution-id]
  (let [find-hakukohteet (fn [hOid] (filter (fn [hk] (= (:hakuOid hk) hOid)) (vals @hakukohteet)))
        ->list-item (fn [t] (into {}
                                  (remove (comp nil? second)
                                          (assoc {}
                                                 :oid (:oid t)
                                                 :koulutusOid (:koulutusOid t)
                                                 :nimi (:nimi t)
                                                 :tila (:tila t)
                                                 :tarjoajat (:tarjoajat t)
                                                 :organisaatioOid (:organisaatioOid t)
                                                 :johtaaTutkintoon (:johtaaTutkintoon t)
                                                 :muokkaaja (:muokkaaja t)
                                                 :modified (:modified t)))))]
    (map (fn [hk] (->list-item (mock-get-toteutus (:toteutusOid hk) (System/currentTimeMillis)))) (find-hakukohteet hakuOid))))

(defn mock-get-hakutiedot-for-koulutus
  [oid execution-id]
  (let [find-toteutukset (fn [oid] (filter (fn [t] (= (:koulutusOid t) oid)) (vals @toteutukset)))
        find-hakukohteet (fn [tOid] (filter (fn [hk] (= (:toteutusOid hk) tOid)) (vals @hakukohteet)))
        assoc-hakukohde (fn [hk] (let [vp (mock-get-valintaperuste (:valintaperuste hk) (System/currentTimeMillis))]
                                   (into {} (remove (comp nil? second)
                                                    (assoc {}
                                                           :hakuajat (:hakuajat hk)
                                                           :tila (:tila hk)
                                                           :nimi (:nimi hk)
                                                           :hakukohdeOid (:oid hk)
                                                           :hakulomakeKuvaus (:hakulomakeKuvaus hk)
                                                           :kaytetaanHaunAikataulua (:kaytetaanHaunAikataulua hk)
                                                           :pohjakoulutusvaatimusKoodiUrit (:pohjakoulutusvaatimusKoodiUrit hk)
                                                           :pohjakoulutusvaatimusTarkenne (:pohjakoulutusvaatimusTarkenne hk)
                                                           :hakulomaketyyppi (:hakulomaketyyppi hk)
                                                           :hakulomakeLinkki (:hakulomakeLinkki hk)
                                                           :hakulomakeAtaruId (:hakulomakeAtaruId hk)
                                                           :jarjestyspaikkaOid (:jarjestyspaikkaOid hk)
                                                           :organisaatioOid (:organisaatioOid hk)
                                                           :muokkaaja (:muokkaaja hk)
                                                           :modified (:modified hk)
                                                           :esikatselu (:esikatselu hk)
                                                           :valintaperusteId (:valintaperusteId hk)
                                                           :aloituspaikat (get-in hk [:metadata :aloituspaikat])
                                                           :hakukohteenLinja (get-in hk [:metadata :hakukohteenLinja])
                                                           :koulutuksenAlkamiskausi (get-in hk [:metadata :koulutuksenAlkamiskausi])
                                                           :jarjestaaUrheilijanAmmKoulutusta true
                                                           :valintatapaKoodiUrit (map :valintatapaKoodiUri
                                                                                      (get-in vp [:metadata :valintatavat])))))))
        assoc-haku (fn [hOid hks] (if-let [haku (mock-get-haku hOid (System/currentTimeMillis))]
                                    (assoc {}
                                           :hakuOid hOid
                                           :hakutapaKoodiUri (:hakutapaKoodiUri haku)
                                           :tila (:tila haku)
                                           :nimi (:nimi haku)
                                           :hakuajat (:hakuajat haku)
                                           :koulutuksenAlkamiskausi (get-in haku [:metadata :koulutuksenAlkamiskausi])
                                           :hakukohteet (vec (map assoc-hakukohde hks)))
                                    nil))
        assoc-haut (fn [hkByH] (map (fn [hOid] (assoc-haku hOid (get hkByH hOid))) (keys hkByH)))
        assoc-toteutus (fn [t] (assoc {} :toteutusOid (:oid t)
                                      :haut (vec (assoc-haut (group-by :hakuOid (find-hakukohteet (:oid t)))))))]
    (vec (map assoc-toteutus (find-toteutukset oid)))))

(defn mock-list-koulutus-oids-by-sorakuvaus
  [sorakuvausId execution-id]
  (let [find-koulutukset (fn [sid] (filter (fn [k] (= (:sorakuvausId k) sid)) (vals @koulutukset)))]
    (map :oid (find-koulutukset sorakuvausId))))

(defn mock-get-oppilaitoksen-osat-by-oppilaitos
  [oppilaitosOid execution-id]
  (filter (fn [osa] (= (:oppilaitosOid osa) oppilaitosOid)) (vals @oppilaitoksen-osat)))

(defn mock-get-last-modified
  [since]
  (-> {}
      (assoc :koulutukset (map :oid (vals @koulutukset)))
      (assoc :toteutukset (map :oid (vals @toteutukset)))
      (assoc :haut (map :oid (vals @haut)))
      (assoc :hakukohteet (map :oid (vals @hakukohteet)))
      (assoc :valintaperusteet (map :id (vals @valintaperusteet)))
      (assoc :sorakuvaukset (map :id (vals @sorakuvaukset)))
      (assoc :oppilaitokset (map :oid (vals @oppilaitokset)))))

(defn reset-indices
  []
  (doseq [index (->> (admin/list-indices-and-aliases)
                     (keys)
                     (map name))]
    (tools/delete-index index)))

(defn indices-fixture
  [tests]
  (tests)
  (reset-indices))

(defn refresh-indices
  []
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.koulutus/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.toteutus/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.haku/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.hakukohde/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.valintaperuste/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.koulutus-search/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.oppilaitos-search/index-name)
  (tools/refresh-index kouta-indeksoija-service.indexer.kouta.oppilaitos/index-name))

(defn delete-all-elastic-data []
  (refresh-indices)
  (let [cat-indices (u/elastic-get (u/elastic-url "_cat" "indices") {:format "json"})
        indices (map :index (filter (fn [i] (and (not (= (:docs.count i) "0")) (not (= (:index i) ".geoip_databases")))) cat-indices))]
    (doseq [index indices]
      (println index)
      (println (u/elastic-post (u/elastic-url index "_delete_by_query") {:query {:match_all {}}} {:conflicts "proceed" :refresh true})))))

(defn reset-mocks
  []
  (reset! koulutukset {})
  (reset! toteutukset {})
  (reset! haut {})
  (reset! hakukohteet {})
  (reset! valintaperusteet {})
  (reset! sorakuvaukset {})
  (reset! oppilaitokset {})
  (reset! oppilaitoksen-osat {}))

(defn init
  []
  (intern 'clj-log.access-log 'service "kouta-indeksoija")
  (admin/initialize-indices))

(defn teardown
  []
  (reset-mocks)
  (clear-all-cached-data)
  (delete-all-elastic-data))

(defn mock-indexing-fixture [test]
  (init)
  (test)
  (teardown))

(defn mock-pohjakoulutusvaatimus-koodi-urit
  [hakutieto]
  ["pohjakoulutusvaatimuskonfo_am"])

(defn mock-koulutustyyppi-koodisto
  [koodisto]
  {:koodit [{:koodiUri "koulutustyyppiabc_01"}]})

(defn toimipiste-children
  [oids]
  (map #(-> {}
            (assoc :oid %)
            (assoc :status "AKTIIVINEN")
            (assoc :kotipaikkaUri "kunta_091")
            (assoc :children [])
            (assoc :nimi {:fi (str "Toimipiste fi " %)
                          :sv (str "Toimipiste sv " %)})) oids))

(defn mock-get-opintokokonaisuudet-by-toteutus-oids
  [oids execution-id]
  [])

(defn mocked-hierarkia-default-entity [oid]
  (println "mocked hierarkia base entity for oid " oid)
  {:organisaatiot [{:oid oid
                    :alkuPvm "694216800000"
                    :kotipaikkaUri "kunta_091"
                    :parentOid (str oid "parent")
                    :kieletUris ["oppilaitoksenopetuskieli_1#1" "oppilaitoksenopetuskieli_2#1"]
                    :parentOidPath "1.2.246.562.10.30705820527/1.2.246.562.10.75341760405/1.2.246.562.10.00000000001"
                    :oppilaitosKoodi "12345"
                    :oppilaitostyyppi "oppilaitostyyppi_42#1"
                    :nimi {:fi (str "Oppilaitos fi " oid)
                           :sv (str "Oppilaitos sv " oid)}
                    :status "AKTIIVINEN"
                    :aliOrganisaatioMaara 3
                    :organisaatiotyypit ["organisaatiotyyppi_03"]
                    :children (toimipiste-children ["1.2.246.562.10.777777777991" "1.2.246.562.10.777777777992" "1.2.246.562.10.777777777993"])}]})

(defn mock-get-organisaatio-by-oid
  [oid]
  (parse (str "test/resources/organisaatiot/" oid ".json")))

(defn mock-get-all-organisaatiot
  []
  (parse (str "test/resources/organisaatiot/hierarkia.json")))

(defmacro with-mocked-indexing
  [& body]
  ;TODO: with-redefs is not thread safe and may cause unexpected behaviour.
  ;It can be temporarily fixed by using locked in mocking functions, but better solution would be superb!
  `(with-redefs [kouta-indeksoija-service.rest.kouta/get-koulutus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-koulutus

                 kouta-indeksoija-service.rest.kouta/get-toteutus-list-for-koulutus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-toteutukset

                 kouta-indeksoija-service.rest.kouta/get-toteutus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-toteutus

                 kouta-indeksoija-service.rest.kouta/get-haku-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-haku

                 kouta-indeksoija-service.rest.kouta/list-hakukohteet-by-haku-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-hakukohteet-by-haku

                 kouta-indeksoija-service.rest.kouta/get-hakukohde-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-hakukohde

                 kouta-indeksoija-service.rest.kouta/get-valintaperuste-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-valintaperuste

                 kouta-indeksoija-service.rest.kouta/get-sorakuvaus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-sorakuvaus

                 kouta-indeksoija-service.rest.kouta/get-oppilaitos-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-oppilaitos

                 kouta-indeksoija-service.rest.kouta/get-oppilaitokset-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-oppilaitokset

                 kouta-indeksoija-service.rest.kouta/get-hakutiedot-for-koulutus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-hakutiedot-for-koulutus

                 kouta-indeksoija-service.rest.kouta/list-haut-by-toteutus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-list-haut-by-toteutus

                 kouta-indeksoija-service.rest.kouta/list-hakukohteet-by-valintaperuste-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-list-hakukohteet-by-valintaperuste

                 kouta-indeksoija-service.rest.kouta/list-toteutukset-by-haku-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-list-toteutukset-by-haku

                 kouta-indeksoija-service.rest.kouta/get-opintokokonaisuudet-by-toteutus-oids-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-opintokokonaisuudet-by-toteutus-oids

                 kouta-indeksoija-service.rest.kouta/get-koulutukset-by-tarjoaja-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-koulutukset-by-tarjoaja

                 kouta-indeksoija-service.rest.kouta/get-koulutus-oids-by-tarjoajat-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-koulutus-oids-by-tarjoajat

                 kouta-indeksoija-service.rest.kouta/get-hakukohde-oids-by-jarjestyspaikat-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-hakukohde-oids-by-jarjestyspaikat

                 kouta-indeksoija-service.rest.kouta/get-toteutus-oids-by-tarjoajat-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-toteutus-oids-by-tarjoajat

                 kouta-indeksoija-service.rest.kouta/list-koulutus-oids-by-sorakuvaus-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-list-koulutus-oids-by-sorakuvaus

                 kouta-indeksoija-service.rest.kouta/get-oppilaitoksen-osat-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-oppilaitoksen-osat-by-oppilaitos

                 kouta-indeksoija-service.rest.kouta/get-last-modified
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-last-modified

                 kouta-indeksoija-service.rest.koodisto/get-koodi-nimi-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-koodisto

                 kouta-indeksoija-service.rest.koodisto/get-koodi-nimi-and-arvo-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-koodi-nimi-and-arvo-with-cache

                 kouta-indeksoija-service.rest.oppijanumerorekisteri/get-henkilo-nimi-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-get-henkilo-nimi-with-cache

                 kouta-indeksoija-service.rest.koodisto/list-alakoodi-nimet-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-alakoodit

                 kouta-indeksoija-service.rest.koodisto/list-ylakoodit-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-ylakoodit

                 kouta-indeksoija-service.rest.eperuste/get-doc
                 kouta-indeksoija-service.fixture.external-services/mock-get-eperuste

                 kouta-indeksoija-service.rest.eperuste/get-doc-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-get-eperuste

                 kouta-indeksoija-service.rest.eperuste/get-osaamisalakuvaukset
                 kouta-indeksoija-service.fixture.external-services/mock-get-osaamisalakuvaukset

                 kouta-indeksoija-service.rest.osaamismerkki/get-doc
                 kouta-indeksoija-service.fixture.external-services/mock-get-osaamismerkki

                 kouta-indeksoija-service.rest.osaamismerkki/get-doc-with-cache
                 kouta-indeksoija-service.fixture.external-services/mock-get-osaamismerkki

                 kouta-indeksoija-service.rest.osaamismerkki/fetch-all
                 kouta-indeksoija-service.fixture.external-services/mock-fetch-all-osaamismerkit

                 kouta-indeksoija-service.indexer.tools.search/pohjakoulutusvaatimus-koodi-urit
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-pohjakoulutusvaatimus-koodi-urit

                 kouta-indeksoija-service.indexer.koodisto.koodisto/get-from-index
                 mock-koulutustyyppi-koodisto

                 kouta-indeksoija-service.rest.kouta/get-pistehistoria-with-cache
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-pistehistoria

                 kouta-indeksoija-service.rest.organisaatio/get-all-organisaatiot
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-all-organisaatiot

                 kouta-indeksoija-service.rest.organisaatio/get-by-oid
                 kouta-indeksoija-service.fixture.kouta-indexer-fixture/mock-get-organisaatio-by-oid]
     (do ~@body)))

(defn mock-get-oppilaitokset
  [oids execution-id]
  (let [oppilaitokset-ja-osat (apply concat
                                     (for [oid oids] [(get @oppilaitokset oid) (get @oppilaitoksen-osat oid)]))]
    {:oppilaitokset (filter some? oppilaitokset-ja-osat)
     :organisaatioHierarkia (mocked-hierarkia-default-entity (first oids))}))

(defn index-oids-without-related-indices
  [oids]
  (with-mocked-indexing
    (with-redefs [kouta-indeksoija-service.rest.kouta/get-last-modified (fn [x] oids)]
      (indexer/index-all-kouta)))
  (refresh-indices))
