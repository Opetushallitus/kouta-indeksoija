(ns kouta-indeksoija-service.indexer.tools.search
  (:require [clj-time.core :as t]
            [clj-time.format :refer [parse]]
            [clojure.set :refer [intersection]]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [kouta-indeksoija-service.indexer.cache.eperuste :refer [filter-tutkinnon-osa get-eperuste-by-id]]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.tools.general :refer [aikuisten-perusopetus? amk? amm-koulutus-with-eperuste? amm-muu? amm-ope-erityisope-ja-opo?
                                                                    amm-osaamisala? amm-tutkinnon-osa? ammatillinen? asiasana->lng-value-map erikoislaakari?
                                                                    erikoistumiskoulutus? get-non-korkeakoulu-koodi-uri julkaistu? kk-opintojakso?
                                                                    kk-opintokokonaisuus? korkeakoulutus? lukio? ope-pedag-opinnot?
                                                                    set-hakukohde-tila-by-related-haku telma? tuva? vapaa-sivistystyo-muu?
                                                                    vapaa-sivistystyo-opistovuosi? yo?]]
            [kouta-indeksoija-service.indexer.tools.koodisto :as koodisto]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [oppilaitostyyppi-uri-to-tyyppi remove-uri-version]]
            [kouta-indeksoija-service.rest.koodisto :refer [extract-versio
                                                            get-koodi-nimi-with-cache]]
            [kouta-indeksoija-service.util.tools :refer [->distinct-vec
                                                         get-esitysnimi]]))

(defonce amm-perustutkinto-erityisopetuksena-koulutustyyppi "koulutustyyppi_4")

(defonce ammatilliset-koulutustyyppi-koodirit ["koulutustyyppi_1" "koulutustyyppi_2" "koulutustyyppi_11" "koulutustyyppi_12" "koulutustyyppi_26"])

(defn clean-uris
  [uris]
  (vec (map remove-uri-version uris)))

(defn koulutusala-koodi-urit
  [koulutus]
  (if (or (amm-koulutus-with-eperuste? koulutus) (amm-osaamisala? koulutus) (amm-tutkinnon-osa? koulutus))
    (let [koulutusKoodiUri (get-non-korkeakoulu-koodi-uri koulutus)]
      (vec (concat (map :koodiUri (koodisto/koulutusalat-taso1 koulutusKoodiUri))
                   (map :koodiUri (koodisto/koulutusalat-taso2 koulutusKoodiUri)))))
    (get-in koulutus [:metadata :koulutusalaKoodiUrit])))

(defn- get-ammatillinen-eperuste
  [koulutus]
  (get-eperuste-by-id (:ePerusteId koulutus)))

(defn tutkintonimike-koodi-urit
  [koulutus]
  (cond (amm-koulutus-with-eperuste? koulutus) (when-let [eperuste (get-ammatillinen-eperuste koulutus)]
                                                 (->distinct-vec (map :tutkintonimikeUri (:tutkintonimikkeet eperuste))))
        (lukio? koulutus) (vector koodisto/koodiuri-ylioppilas-tutkintonimike)
        :else (get-in koulutus [:metadata :tutkintonimikeKoodiUrit] [])))

(defn koulutustyyppi-koodi-urit
  [koulutus]
  (if (ammatillinen? koulutus)
    (vec (map :koodiUri (koodisto/koulutustyypit (get-non-korkeakoulu-koodi-uri koulutus))))
    []))

(defn- get-osaamisala
  [eperuste koulutus]
  (when-let [osaamisalaKoodiUri (some-> koulutus :metadata :osaamisalaKoodiUri remove-uri-version)]
    (some->> (:osaamisalat eperuste)
             (filter #(= osaamisalaKoodiUri (some-> % :koodiUri remove-uri-version)))
             (first))))

(defn opintojen-laajuus-koodi-uri
  [koulutus]
  (cond
    (ammatillinen? koulutus)   (-> koulutus (get-ammatillinen-eperuste) (get-in [:opintojenLaajuus :koodiUri]))
    (amm-osaamisala? koulutus) (-> koulutus (get-ammatillinen-eperuste) (get-osaamisala koulutus) (get-in [:opintojenLaajuus :koodiUri]))
    :else nil))

(defn number-or-nil
  [koodiarvo]
  (if (every? #(Character/isDigit %) koodiarvo)
    koodiarvo
    nil))

(defn opintojen-laajuus-numero
  [koulutus]
  (cond
    (amm-koulutus-with-eperuste? koulutus)            (-> koulutus (get-ammatillinen-eperuste) :opintojenLaajuusNumero)
    (amm-osaamisala? koulutus)          (-> koulutus (get-ammatillinen-eperuste) (get-osaamisala koulutus) :opintojenLaajuusNumero)
    (or
     (ammatillinen? koulutus) ; ilman ePerustetta
     (amm-muu? koulutus)
     (yo? koulutus)
     (amk? koulutus)
     (amm-ope-erityisope-ja-opo? koulutus)
     (ope-pedag-opinnot? koulutus)
     (tuva? koulutus)
     (telma? koulutus)
     (lukio? koulutus)
     (vapaa-sivistystyo-opistovuosi? koulutus)
     (vapaa-sivistystyo-muu? koulutus)
     (aikuisten-perusopetus? koulutus))        (get-in koulutus [:metadata :opintojenLaajuusNumero])
    :else nil))

(defn opintojen-laajuus-numero-min
  [koulutus]
  (cond
    (or
     (kk-opintojakso? koulutus)
     (kk-opintokokonaisuus? koulutus)
     (erikoistumiskoulutus? koulutus)) (get-in koulutus [:metadata :opintojenLaajuusNumeroMin])
    :else nil))

(defn opintojen-laajuus-numero-max
  [koulutus]
  (cond
    (or
     (kk-opintojakso? koulutus)
     (kk-opintokokonaisuus? koulutus)
     (erikoistumiskoulutus? koulutus)) (get-in koulutus [:metadata :opintojenLaajuusNumeroMax])
    :else nil))

(defn opintojen-laajuusyksikko-koodi-uri
  [koulutus]
  (cond
    (amm-koulutus-with-eperuste? koulutus)   (-> koulutus (get-ammatillinen-eperuste) (get-in [:opintojenLaajuusyksikko :koodiUri]))
    (amm-osaamisala? koulutus) (-> koulutus (get-ammatillinen-eperuste) (get-in [:opintojenLaajuusyksikko :koodiUri]))
    (and (korkeakoulutus? koulutus) (not (erikoislaakari? koulutus))) koodisto/koodiuri-opintopiste-laajuusyksikko
    (lukio? koulutus) koodisto/koodiuri-opintopiste-laajuusyksikko
    (tuva? koulutus) koodisto/koodiuri-viikko-laajuusyksikko
    (telma? koulutus) koodisto/koodiuri-osaamispiste-laajuusyksikko
    (vapaa-sivistystyo-opistovuosi? koulutus) koodisto/koodiuri-opintopiste-laajuusyksikko
    (or
     (ammatillinen? koulutus) ;ilman ePerustetta
     (vapaa-sivistystyo-muu? koulutus)
     (aikuisten-perusopetus? koulutus)
     (amm-muu? koulutus)) (get-in koulutus [:metadata :opintojenLaajuusyksikkoKoodiUri])
    :else nil))

(defn tutkinnon-osat
  [koulutus]
  (when (amm-tutkinnon-osa? koulutus)
    (-> (for [tutkinnon-osa (get-in koulutus [:metadata :tutkinnonOsat])
              :let [eperuste-id (:ePerusteId tutkinnon-osa)
                    eperuste (get-eperuste-by-id eperuste-id)
                    eperuste-tutkinnon-osa (filter-tutkinnon-osa eperuste (:tutkinnonosaId tutkinnon-osa))]]
          {:eperuste eperuste-id
           :koulutus (:koulutusKoodiUri tutkinnon-osa)
           :opintojenLaajuusNumero (some-> eperuste-tutkinnon-osa :opintojenLaajuusNumero)
           :opintojenLaajuus (some-> eperuste-tutkinnon-osa :opintojenLaajuus :koodiUri)
           :opintojenLaajuusyksikko (get-in eperuste [:opintojenLaajuusyksikko :koodiUri])
           :tutkinnonOsatKoodiUri (some-> eperuste-tutkinnon-osa :koodiUri)}))))

(defn osaamisala-koodi-uri
  [koulutus]
  (some-> (get-ammatillinen-eperuste koulutus)
          (get-osaamisala koulutus)
          (:koodiUri)))

(defn koulutustyyppi-for-organisaatio
  [organisaatio]
  (when-let [oppilaitostyyppi (:oppilaitostyyppi organisaatio)]
    (oppilaitostyyppi-uri-to-tyyppi oppilaitostyyppi)))

(defn- has-alakoodi
  [koodi alakoodit]
  (some #(= (:koodiUri %) (:koodi (extract-versio koodi)))
        alakoodit))

(defn- find-konfo-alakoodit
  [koodiUri]
  (filter #(->> %
                (:alakoodit)
                (has-alakoodi koodiUri))
          (koodisto/pohjakoulutusvaatimuskonfo)))

(defn- map-to-konfo-koodit
  [koutaKoodiUrit]
  (->> koutaKoodiUrit
       (map find-konfo-alakoodit)
       (flatten)
       (map :koodiUri)
       (->distinct-vec)))

; NOTE: kouta - konfo pohjakoulutuskoodit ovat suhteessa * : * joten jokaista koutakoodia vastaa taulukko konfokoodeja
(defn pohjakoulutusvaatimus-koodi-urit
  [hakukohde]
  (->> hakukohde
       (:pohjakoulutusvaatimusKoodiUrit)
       (map-to-konfo-koodit)))

(defn- tutkintotyyppi->koulutustyyppi
  [tutkintotyyppit]
  (cond
    (= tutkintotyyppit ["tutkintotyyppi_06"]) ["amk-alempi"]
    (= tutkintotyyppit ["tutkintotyyppi_12"]) ["amk-ylempi"]
    (= (set tutkintotyyppit) (set ["tutkintotyyppi_13"])) ["kandi"]
    (= (set tutkintotyyppit) (set ["tutkintotyyppi_14"])) ["maisteri"]
    (= (set tutkintotyyppit) (set ["tutkintotyyppi_16"])) ["tohtori"]
    (clojure.set/subset? (set ["tutkintotyyppi_13" "tutkintotyyppi_14"]) (set tutkintotyyppit)) ["kandi-ja-maisteri"]
    :else []))

(defn- get-korkeakoulutus-koulutustyyppi
  [koulutus]
  (let [tutkintotyyppi-koodi-urit (->> (:koulutuksetKoodiUri koulutus)
                                       (mapcat #(koodisto/tutkintotyypit %))
                                       (map :koodiUri))]
    (tutkintotyyppi->koulutustyyppi (distinct tutkintotyyppi-koodi-urit))))


(defn- muu-ammatillinen-tutkinto-koulutus? [koulutus koulutustyyppikoodit]
  (and (ammatillinen? koulutus) (empty? (intersection (set koulutustyyppikoodit) (set ammatilliset-koulutustyyppi-koodirit)))))

;Konfo-ui:n koulutushaun koulutustyyppi filtteriä varten täytyy tallentaa erinäisiä hakusanoja
;koulutus-search indeksin jarjestajan metadatan koulutustyyppi kenttään.
;Tämä koostuu näistä paloista:
; 1. Ammatillisille haetaan koulutusKoodiUrilla koodistopalvelusta vastaava koulutustyyppiKoodiUri
;    esim. koulutustyyppi_26 =	Ammatillinen perustutkinto tai koulutustyyppi_12 = Erikoisammattitutkinto.
;    Korkeakoulutuksille ei täältä haeta, koska kyseinen koodisto ei erittele korkeakoulutuksia vaan sisältää vain
;    koodin koulutustyyppi_3 = Korkeakoulutus
;
;2. Kaikille tallennetaan koutan sisäinen koulutustyyppi arvo, joka on kouta-ui:lla koulutukselle valittu.
;   Esim. amm, amk, yo, lk, muu, amm-tutkinnon-osa
;
;3. Korkeakoulutuksille tallennetaan kovakoodattu string, joka päätellään koulutusKoodiUrin avulla
;   tutkintotyyppi koodistosta. Esim tutkintotyyppi_13 = kandi tai tutkintotyyppi_12 = amk-ylempi.
;
;4. Jos ammatilliselle toteutukselle on valittu amm-perustutkinto-erityisopetuksena kenttä, tallennetaan
;   kovakoodattu arvo koulutustyyppi_4 = Ammatillinen perustutkinto erityisopetuksena
;
;Lopputulos on taulukko stringejä, esim. ["amm" "koulutustyyppi_26"] tai ["yo" "maisteri"]
(defn- add-korkeakoulutus-tyypit-from-koulutus-koodi
  [koulutus koulutustyypit]
  (if (korkeakoulutus? koulutus)
    (concat koulutustyypit (get-korkeakoulutus-koulutustyyppi koulutus))
    koulutustyypit))

(defn deduce-koulutustyypit
  ([koulutus toteutus-metadata]
   (let [koulutustyyppi (:koulutustyyppi koulutus)
         koulutustyyppikoodit (koulutustyyppi-koodi-urit koulutus)
         koulutustyypit-without-erityisopetus (filter #(not= % amm-perustutkinto-erityisopetuksena-koulutustyyppi) koulutustyyppikoodit)
         amm-erityisopetuksena? (:ammatillinenPerustutkintoErityisopetuksena toteutus-metadata)
         tuva-erityisopetuksena? (:jarjestetaanErityisopetuksena toteutus-metadata)
         avoin-korkeakoulutus? (get-in koulutus [:metadata :isAvoinKorkeakoulutus])]
     (->> (cond
            (and (tuva? koulutus) (not= toteutus-metadata nil)) [koulutustyyppi (if tuva-erityisopetuksena? "tuva-erityisopetus" "tuva-normal")]
            (vapaa-sivistystyo-opistovuosi? koulutus) ["vapaa-sivistystyo" koulutustyyppi]
            (vapaa-sivistystyo-muu? koulutus) ["vapaa-sivistystyo" koulutustyyppi]
            (amm-ope-erityisope-ja-opo? koulutus) ["amk-muu" koulutustyyppi]
            (kk-opintojakso? koulutus) ["kk-muu" koulutustyyppi (if avoin-korkeakoulutus? "kk-opintojakso-avoin" "kk-opintojakso-normal")]
            (erikoislaakari? koulutus) ["kk-muu" koulutustyyppi]
            (kk-opintokokonaisuus? koulutus) ["kk-muu" koulutustyyppi (if avoin-korkeakoulutus? "kk-opintokokonaisuus-avoin" "kk-opintokokonaisuus-normal")]
            (ope-pedag-opinnot? koulutus) ["kk-muu" koulutustyyppi]
            (erikoistumiskoulutus? koulutus) ["kk-muu" koulutustyyppi]
            (amm-tutkinnon-osa? koulutus) ["muut-ammatilliset" koulutustyyppi]
            (amm-osaamisala? koulutus) ["muut-ammatilliset" koulutustyyppi]
            (telma? koulutus) ["muut-ammatilliset" koulutustyyppi]
            (amm-muu? koulutus) ["muut-ammatilliset" koulutustyyppi]
            (ammatillinen? koulutus) (cond
                                       amm-erityisopetuksena? [koulutustyyppi amm-perustutkinto-erityisopetuksena-koulutustyyppi]
                                       (muu-ammatillinen-tutkinto-koulutus? koulutus koulutustyyppikoodit) [koulutustyyppi "muu-amm-tutkinto"]
                                       :else (concat [koulutustyyppi] koulutustyypit-without-erityisopetus))
            :else [koulutustyyppi])
          (add-korkeakoulutus-tyypit-from-koulutus-koodi koulutus))))
  ([koulutus]
   (deduce-koulutustyypit koulutus nil)))

(defn- get-toteutuksen-hakutieto
  [hakutiedot t]
  (first (filter (fn [x] (= (:toteutusOid x) (:oid t))) hakutiedot)))

(defn- get-haun-julkaistut-hakukohteet
  [haku]
  (let [hakukohteet (map #(set-hakukohde-tila-by-related-haku % haku) (:hakukohteet haku))]
    (filter julkaistu? hakukohteet)))

(defn- filter-hakutiedon-haut-julkaistu-and-not-empty-hakukohteet
  [hakutiedon-haut]
  (->> hakutiedon-haut
       (map (fn [haku] (assoc haku
                              :hakukohteet
                              (get-haun-julkaistut-hakukohteet haku))))
       (filter #(seq (:hakukohteet %)))))

(defn get-toteutuksen-julkaistut-hakutiedot
  [hakutiedot t]
  (let [toteutuksen-hakutiedot (get-toteutuksen-hakutieto hakutiedot t)]
    (assoc toteutuksen-hakutiedot
           :haut
           (filter-hakutiedon-haut-julkaistu-and-not-empty-hakukohteet (:haut toteutuksen-hakutiedot)))))

(defn- remove-nils-from-search-terms
  [search-terms]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (not-empty (into {} (remove (comp nil? second)) x))
       x))
   search-terms))

(defn- get-lang-values
  [lang values]
  (distinct (remove nil? (map #(lang %) values))))

(defn- stringify-tarkka-ajankohta [time-str]
  (when-let [date (parse time-str)]
    (str (t/year date) "-" (if (>= (t/month date) 8) "syksy" "kevat"))))

(defn- stringify-kausi-ja-vuosi [kausi-ja-vuosi]
  (when-let [koodiUri (:koulutuksenAlkamiskausiKoodiUri kausi-ja-vuosi)]
    (let [vuosi (:koulutuksenAlkamisvuosi kausi-ja-vuosi)
          kausi (if (string/starts-with? koodiUri "kausi_s") "syksy" "kevat")]
      (str vuosi "-" kausi))))

(defn- stringify-alkamiskausi [alkamiskausi]
  (case (:alkamiskausityyppi alkamiskausi)
    "tarkka alkamisajankohta" (stringify-tarkka-ajankohta (:koulutuksenAlkamispaivamaara alkamiskausi))
    "alkamiskausi ja -vuosi" (stringify-kausi-ja-vuosi alkamiskausi)
    "henkilokohtainen suunnitelma" "henkilokohtainen"
    nil))

(defn- add-if-some-missing [addition x]
  (if (or (empty? x) (some nil? x))
    (into [addition] (remove nil? x))
    x))

(defn get-hakutiedon-paatellyt-alkamiskaudet [toteutus hakutieto]
  (->> (filter julkaistu? (:haut hakutieto))
       (mapcat (fn [haku]
                 (->> (filter julkaistu? (:hakukohteet haku))
                      (map (fn [hakukohde]
                             (when (not (:kaytetaanHaunAlkamiskautta hakukohde))
                               (stringify-alkamiskausi (get-in hakukohde [:koulutuksenAlkamiskausi])))))
                      (add-if-some-missing (stringify-alkamiskausi (get-in haku [:koulutuksenAlkamiskausi]))))))
       distinct
       (add-if-some-missing (stringify-alkamiskausi (get-in toteutus [:metadata :opetus :koulutuksenAlkamiskausi])))))

(defn get-paatellyt-alkamiskaudet [toteutukset hakutiedot]
  (let [hakutiedot-by-toteutus-oid (into {} (map (fn [hakutieto] [(keyword (:toteutusOid hakutieto)) hakutieto]) hakutiedot))]
    (distinct (->> (filter julkaistu? toteutukset)
                   (mapcat (fn [toteutus]
                             (get-hakutiedon-paatellyt-alkamiskaudet
                              toteutus
                              (get-in hakutiedot-by-toteutus-oid [(keyword (:oid toteutus))]))))))))

(defn assoc-paatellyt-alkamiskaudet [koulutus toteutukset hakutiedot]
  (assoc koulutus :paatellytAlkamiskaudet (get-paatellyt-alkamiskaudet toteutukset hakutiedot)))


(defn- kaytetaanHaunAikatauluaHakukohteessa?
  [hakukohde]
  (true? (:kaytetaanHaunAikataulua hakukohde)))

(defn- get-hakukohde-hakutieto
  [hakukohde haku]
  (-> {}
      (assoc :hakuajat (:hakuajat (if (kaytetaanHaunAikatauluaHakukohteessa? hakukohde) haku hakukohde)))
      (assoc :hakutapa (remove-uri-version (:hakutapaKoodiUri haku)))
      (assoc :yhteishakuOid (when (= koodisto/koodiuri-yhteishaku-hakutapa (remove-uri-version (:hakutapaKoodiUri haku))) (:hakuOid haku)))
      (assoc :pohjakoulutusvaatimukset (clean-uris (pohjakoulutusvaatimus-koodi-urit hakukohde)))
      (assoc :valintatavat (clean-uris (:valintatapaKoodiUrit hakukohde)))
      (assoc :jarjestaaUrheilijanAmmKoulutusta (:jarjestaaUrheilijanAmmKoulutusta hakukohde))))

(defn- map-haut
  [haku]
  (map #(get-hakukohde-hakutieto % haku) (:hakukohteet haku)))

(defn get-search-hakutiedot
  [hakutiedot]
  (->> hakutiedot
       (mapcat :haut)
       (map map-haut)
       flatten
       vec))

(defn search-terms
  [& {:keys [koulutus
             toteutus
             oppilaitos
             tarjoajat
             jarjestaa-urheilijan-amm-koulutusta
             hakutiedot
             toteutus-organisaationimi
             opetuskieliUrit
             koulutustyypit
             kuva
             onkoTuleva
             nimi
             lukiopainotukset
             lukiolinjat_er
             osaamisalat
             metadata]
      :or   {koulutus                  []
             toteutus                  []
             oppilaitos                []
             tarjoajat                 []
             jarjestaa-urheilijan-amm-koulutusta nil
             hakutiedot                []
             toteutus-organisaationimi {}
             opetuskieliUrit           []
             koulutustyypit            []
             kuva                      nil
             onkoTuleva                nil
             nimi                      {}
             lukiopainotukset          []
             lukiolinjat_er            []
             osaamisalat               []
             metadata                  {}}}]

  (let [tutkintonimikkeet (vec (map #(-> % get-koodi-nimi-with-cache :nimi) (tutkintonimike-koodi-urit koulutus)))
        toteutus-metadata (:metadata toteutus)
        ammattinimikkeet (asiasana->lng-value-map (get-in toteutus-metadata [:ammattinimikkeet]))
        asiasanat (flatten (get-in toteutus-metadata [:asiasanat]))
        kunnat (remove nil? (distinct (map :kotipaikkaUri tarjoajat)))
        maakunnat (remove nil? (distinct (map #(:koodiUri (koodisto/maakunta %)) kunnat)))
        toteutus-nimi (get-esitysnimi toteutus)]
    (remove-nils-from-search-terms
     {:koulutusOid               (:oid koulutus)
      :koulutusnimi              {:fi (:fi (:nimi koulutus))
                                  :sv (:sv (:nimi koulutus))
                                  :en (:en (:nimi koulutus))}
      :koulutus_organisaationimi {:fi (:fi (:nimi oppilaitos))
                                  :sv (:sv (:nimi oppilaitos))
                                  :en (:en (:nimi oppilaitos))}
      :toteutusOid               (:oid toteutus)
      :toteutusNimi              {:fi (:fi toteutus-nimi)
                                  :sv (:sv toteutus-nimi)
                                  :en (:en toteutus-nimi)}
      :toteutusHakuaika          (get toteutus-metadata :hakuaika {})
      :oppilaitosOid             (:oid oppilaitos)
      :toteutus_organisaationimi {:fi (not-empty (get-lang-values :fi toteutus-organisaationimi))
                                  :sv (not-empty (get-lang-values :sv toteutus-organisaationimi))
                                  :en (not-empty (get-lang-values :en toteutus-organisaationimi))}
      :asiasanat                 {:fi (not-empty (distinct (map #(get % :arvo) (filter #(= (:kieli %) "fi") asiasanat))))
                                  :sv (not-empty (distinct (map #(get % :arvo) (filter #(= (:kieli %) "sv") asiasanat))))
                                  :en (not-empty (distinct (map #(get % :arvo) (filter #(= (:kieli %) "en") asiasanat))))}
      :tutkintonimikkeet         {:fi (not-empty (get-lang-values :fi tutkintonimikkeet))
                                  :sv (not-empty (get-lang-values :sv tutkintonimikkeet))
                                  :en (not-empty (get-lang-values :en tutkintonimikkeet))}
      :ammattinimikkeet          {:fi (not-empty (get-lang-values :fi ammattinimikkeet))
                                  :sv (not-empty (get-lang-values :sv ammattinimikkeet))
                                  :en (not-empty (get-lang-values :en ammattinimikkeet))}
      :sijainti                  (clean-uris (concat kunnat maakunnat))
      :koulutusalat              (not-empty (clean-uris (koulutusala-koodi-urit koulutus)))
      :hakutiedot                (not-empty (get-search-hakutiedot hakutiedot))
      :opetustavat               (not-empty (clean-uris (or (some-> toteutus :metadata :opetus :opetustapaKoodiUrit) [])))
      :opetuskielet              (not-empty (clean-uris opetuskieliUrit))
      :koulutustyypit            (clean-uris koulutustyypit)
      :onkoTuleva                onkoTuleva
      :kuva                      kuva
      :nimi                      (not-empty nimi)
      :metadata                  (common/decorate-koodi-uris (merge metadata {:kunnat kunnat}))
      :lukiopainotukset          (clean-uris lukiopainotukset)
      :lukiolinjaterityinenkoulutustehtava (clean-uris lukiolinjat_er)
      :osaamisalat               (clean-uris osaamisalat)
      :hasJotpaRahoitus          (get toteutus-metadata :hasJotpaRahoitus false)
      :isTyovoimakoulutus        (get toteutus-metadata :isTyovoimakoulutus false)
      :isTaydennyskoulutus       (get toteutus-metadata :isTaydennyskoulutus false)
      :jarjestaaUrheilijanAmmKoulutusta jarjestaa-urheilijan-amm-koulutusta
      :paatellytAlkamiskaudet    (get-paatellyt-alkamiskaudet [toteutus] hakutiedot)})))

(defn jarjestaako-tarjoaja-urheilijan-amm-koulutusta
  [tarjoaja-oids haut]
  (let [hakukohteet (apply concat (for [haku haut]
                                    (:hakukohteet haku)))]
    (if (seq hakukohteet)
      (let [hakukohteet (group-by :jarjestyspaikkaOid hakukohteet)
            tarjoaja-hakukohteet (apply concat (for [tarjoaja-oid tarjoaja-oids]
                                                 (get hakukohteet tarjoaja-oid)))]
        (boolean
         (some
          true?
          (for [hakukohde tarjoaja-hakukohteet]
            (:jarjestaaUrheilijanAmmKoulutusta hakukohde)))))
      false)))

(defn jarjestaako-toteutus-urheilijan-amm-koulutusta
  [haut]
  (let [hakukohteet (apply concat (for [haku haut]
                                    (:hakukohteet haku)))]
    (if (seq hakukohteet)
      (boolean (some #(true? (:jarjestaaUrheilijanAmmKoulutusta %)) hakukohteet))
      false)))

(defn kesto-kuukausina
  [opetus]
  (let [vuosia (get opetus :suunniteltuKestoVuodet 0)
        kuukausia (get opetus :suunniteltuKestoKuukaudet 0)]
    (+ (* 12 vuosia) kuukausia)))
