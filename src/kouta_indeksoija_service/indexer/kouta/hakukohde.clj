(ns kouta-indeksoija-service.indexer.kouta.hakukohde
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.set :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.indexer.indexable :as indexable]
            [kouta-indeksoija-service.indexer.koodisto.koodisto :as koodisto]
            [kouta-indeksoija-service.indexer.kouta.common :as common]
            [kouta-indeksoija-service.indexer.tools.general :refer [get-non-korkeakoulu-koodi-uri korkeakoulutus? korkeakoulutus? not-poistettu?
                                                                    set-hakukohde-tila-by-related-haku Tallennettu]]
            [kouta-indeksoija-service.indexer.tools.koodisto :as koodisto-tools :refer [koulutusasteet]]
            [kouta-indeksoija-service.indexer.tools.koulutustyyppi :refer [assoc-koulutustyyppi-path]]
            [kouta-indeksoija-service.indexer.tools.tyyppi :refer [remove-uri-version]]
            [kouta-indeksoija-service.rest.kouta :as kouta-backend]
            [kouta-indeksoija-service.util.tools :refer [assoc-hakukohde-nimi-as-esitysnimi
                                                         kevat-date?]]))

(def index-name "hakukohde-kouta")
(defonce amm-perustutkinto-erityisopetus-koulutustyyppi "koulutustyyppi_4")
(defonce tuva-koulutustyyppi "koulutustyyppi_40")
(defonce telma-koulutustyyppi "koulutustyyppi_5")
(defonce vapaa-sivistava-koulutustyyppi "koulutustyyppi_10")
(defonce tuva-erityisopetus-koulutustyyppi "koulutustyyppi_41")
(defonce lukio-koulutustyyppi "koulutustyyppi_2")
(defonce painotettavat-oppiaineet-lukiossa-kaikki #{"painotettavatoppiaineetlukiossa_a1"
                                                    "painotettavatoppiaineetlukiossa_a2"
                                                    "painotettavatoppiaineetlukiossa_b1"
                                                    "painotettavatoppiaineetlukiossa_b2"
                                                    "painotettavatoppiaineetlukiossa_b3"})

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

(defn- assoc-hakuajat
  "Asetetaan hakukohteelle haun hakuajat, jos sillä ei ole omia määriteltyjä
   hakuaikoja"
  [hakukohde haku]
  (let [hakukohteen-hakuajat (:hakuajat hakukohde)]
    (assoc hakukohde :hakuajat (if (seq hakukohteen-hakuajat)
                                 hakukohteen-hakuajat
                                 (map #(dissoc % :hakuaika-id)
                                      (:hakuajat haku))))))

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
    (str/starts-with? alkamiskausi-koodi-uri "kausi_k#")
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
  (not (str/blank? (:kohdejoukonTarkenneKoodiUri haku))))

(defn- jatkotutkintohaku-tarkenne?
  [haku]
  (str/starts-with?
   (:kohdejoukonTarkenneKoodiUri haku)
   "haunkohdejoukontarkenne_3#"))

(defn- erasmus-mundus-tarkenne?
  [haku]
  (str/starts-with?
    (:kohdejoukonTarkenneKoodiUri haku)
    "haunkohdejoukontarkenne_010#"))

(defn- yps-tarkenne?
  [haku]
  (or (jatkotutkintohaku-tarkenne? haku)
      (erasmus-mundus-tarkenne? haku)))

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
              (not (yps-tarkenne? haku)))
         (->ei-yps (str "Haun kohdejoukon tarkenne on "
                        (:kohdejoukonTarkenneKoodiUri haku)))

         :else
         yps)))

(defn- assoc-hakulomake-linkki
  [hakukohde haku]
  (let [link-holder (if (true? (:kaytetaanHaunHakulomaketta hakukohde)) haku hakukohde)]
    (conj hakukohde (common/create-hakulomake-linkki-for-hakukohde link-holder (:oid hakukohde)))))

(defn- use-special-koulutus [toteutus koulutus]
  (let [koulutuksentyyppi (get-in koulutus [:metadata :tyyppi])]
    (cond
      (true? (get-in toteutus [:metadata :ammatillinenPerustutkintoErityisopetuksena]))
      amm-perustutkinto-erityisopetus-koulutustyyppi
      (true? (get-in toteutus [:metadata :jarjestetaanErityisopetuksena]))
      tuva-erityisopetus-koulutustyyppi
      (= koulutuksentyyppi "tuva")
      tuva-koulutustyyppi
      (= koulutuksentyyppi "telma")
      telma-koulutustyyppi
      (= koulutuksentyyppi "vapaa-sivistystyo-muu")
      vapaa-sivistava-koulutustyyppi
      (= koulutuksentyyppi "vapaa-sivistystyo-opistovuosi")
      vapaa-sivistava-koulutustyyppi)))

(defn- filter-expired-koodis
  [koodit]
  (let [aktiivisetkoulutustyypit (->> (koodisto/get-from-index "koulutustyyppi")
                                      :koodit
                                      (map #(:koodiUri %)))]
    (filter #(some (partial = %) aktiivisetkoulutustyypit) koodit)))

(defn- get-koulutustyyppikoodi-from-koodisto
  [koulutus]
  (let [code-state-not-passive #(not (= "PASSIIVINEN" (:tila %)))
        koodiurit (->> koulutus
                       :koulutuksetKoodiUri
                       (mapcat koodisto-tools/koulutustyypit)
                       (filter code-state-not-passive)
                       (map :koodiUri)
                       (filter-expired-koodis)
                       (distinct)
                       (filter #(not (.contains [amm-perustutkinto-erityisopetus-koulutustyyppi tuva-erityisopetus-koulutustyyppi] %))))]
    (cond
      (= 1 (count koodiurit))                               ; ei tehdä päättelyä useamman koulutustyypin välillä, vaan jätetään arvoksi nil paitsi jos lukiokoulutus löytyy
      (first koodiurit)

      (seq (filter #(= lukio-koulutustyyppi %) koodiurit))
      lukio-koulutustyyppi)))

(defn- assoc-koulutustyypit
  [hakukohde toteutus koulutus]
  (let [specialkoodi (use-special-koulutus toteutus koulutus)
        koulutustyyppikoodi (if (not (nil? specialkoodi))
                              specialkoodi
                              (get-koulutustyyppikoodi-from-koodisto koulutus))]
    (assoc hakukohde :koulutustyyppikoodi koulutustyyppikoodi)))

(defn- englannikielinen-lukiokoulutus?
  [toteutus koulutus non-korkeakoulu-koodi-uri]
  (and (= (:koulutustyyppi koulutus) "lk")
       (= "koulutus_309902" (first (str/split non-korkeakoulu-koodi-uri #"#"))) ; OY-5519 rajataan myös koulutuskoodin mukaan
       (= (->> (get-in toteutus [:metadata :opetus :opetuskieliKoodiUrit])
               (map #(first (str/split % #"#")))
               (vec))
          ["oppilaitoksenopetuskieli_4"])))

(defn- assoc-onko-harkinnanvarainen-koulutus
  [hakukohde toteutus koulutus]
  (let [non-korkeakoulu-koodi-uri (get-non-korkeakoulu-koodi-uri koulutus)
        hakukohde-nimi-koodi-uri (get-in hakukohde [:hakukohde :koodiUri])
        ; Alla oleva funktio palauttaa koodirelaatioiden kautta esimerkiksi seuraavilla ehdoilla:
        ; - Hakukohde kuuluu koulutukseen "Media-alan perustutkinto" = false
        ; - Hakukohde kuuluu koulutukseen "Hius- ja kauneudenhoitoalan perustutkinto" = true
        ; - Hakukohde on "Perustason ensihoidon osaamisala (Sosiaali- ja terveysalan perustutkinto)" = false
        kysytaanko-harkinnanvaraisuutta-lomakkeella (fn [koodi-uri]
                                                      (nil? (koodisto-tools/harkinnanvaraisuutta-ei-kysyta-lomakkeella koodi-uri)))
        ; Seuraava arvo on tosi esimerkille: "Hakukohde kuuluu koulutukseen "Hius- ja kauneudenhoitoalan perustutkinto"
        harkinnanvaraisuus-question-allowed (and
                                             (some? non-korkeakoulu-koodi-uri)
                                             (not (englannikielinen-lukiokoulutus? toteutus koulutus non-korkeakoulu-koodi-uri))
                                             (kysytaanko-harkinnanvaraisuutta-lomakkeella non-korkeakoulu-koodi-uri)
                                             (not (true? (get-in toteutus [:metadata :ammatillinenPerustutkintoErityisopetuksena])))
                                             (not (.contains ["telma" "tuva" "vapaa-sivistystyo-opistovuosi"] (:koulutustyyppi koulutus)))
                                             (or (nil? hakukohde-nimi-koodi-uri)
                                                 (kysytaanko-harkinnanvaraisuutta-lomakkeella hakukohde-nimi-koodi-uri)))
        ; Seuraava arvo on tosi seuraaville esimerkeille:
        ; - "Hakukohde kuuluu koulutukseen "Hius- ja kauneudenhoitoalan perustutkinto"
        ; - "Hakukohde on "Perustason ensihoidon osaamisala (Sosiaali- ja terveysalan perustutkinto)"
        hakukohde-allows-harkinnanvaraiset-applicants (or harkinnanvaraisuus-question-allowed
                                                          (and
                                                           (some? non-korkeakoulu-koodi-uri)
                                                           (not (englannikielinen-lukiokoulutus? toteutus koulutus non-korkeakoulu-koodi-uri))
                                                           (and (some? hakukohde-nimi-koodi-uri)
                                                                 ;Jos hakukohteella on relaatio ei-harkinnanvaraisuutta koodiin "Harkinnanvaraisuutta ei kysytä lomakkeella", se on automaattisesti harkinnanvarainen
                                                                (not (true? (get-in toteutus [:metadata :ammatillinenPerustutkintoErityisopetuksena])))
                                                                (or (not (kysytaanko-harkinnanvaraisuutta-lomakkeella non-korkeakoulu-koodi-uri))
                                                                    (not (kysytaanko-harkinnanvaraisuutta-lomakkeella hakukohde-nimi-koodi-uri))))))]
    (assoc hakukohde :salliikoHakukohdeHarkinnanvaraisuudenKysymisen harkinnanvaraisuus-question-allowed
           :voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita hakukohde-allows-harkinnanvaraiset-applicants)))

(defn- parse-tarkka-ajankohta [time-str]
  (when-let [date (f/parse time-str)]
    {:kausiUri (if (kevat-date? date)
                 "kausi_k#1"
                 "kausi_s#1")
     :vuosi    (t/year date)}))

(defn- parse-alkamiskausi [alkamiskausi oid]
  (let [tyyppi (:alkamiskausityyppi alkamiskausi)
        result (case tyyppi
                 "tarkka alkamisajankohta" (parse-tarkka-ajankohta (:koulutuksenAlkamispaivamaara alkamiskausi))
                 "alkamiskausi ja -vuosi" {:kausiUri (:koulutuksenAlkamiskausiKoodiUri alkamiskausi) :vuosi (:koulutuksenAlkamisvuosi alkamiskausi)}
                 {})]
    (when (and (:kausiUri result) (:vuosi result))
      (merge result
             {:alkamiskausityyppi tyyppi
              :source             oid}))))

(defn- assoc-paatelty-alkamiskausi-for-hakukohde [hakukohde hakukohde-source haku toteutus]
  (if-let [result (or (parse-alkamiskausi (get-in hakukohde-source [:metadata :koulutuksenAlkamiskausi]) (:oid hakukohde))
                      (parse-alkamiskausi (get-in haku [:metadata :koulutuksenAlkamiskausi]) (:oid haku))
                      (parse-alkamiskausi (get-in toteutus [:metadata :opetus :koulutuksenAlkamiskausi]) (:oid toteutus)))]
    (assoc hakukohde :paateltyAlkamiskausi result)
    hakukohde))

(defn- get-koodiurit-to-complete
  [koodiurit]
  (flatten
   (filter #(contains? painotettavat-oppiaineet-lukiossa-kaikki (remove-uri-version (get-in % [:koodiUrit :oppiaine]))) (set koodiurit))))

(defn- get-koodisto-koodiurit
  [koodiurit koodiurit-to-complete]
  (flatten
   (seq (s/difference (set koodiurit) (set koodiurit-to-complete)))))

(defn- replace-with-koodisto-oppiaineet
  [koodiuri]
  (let [koodisto-oppiaineet (filter
                              #(and (str/starts-with? % (remove-uri-version (get-in koodiuri [:koodiUrit :oppiaine])))
                                    (not= % (get-in koodiuri [:koodiUrit :oppiaine])))
                              (koodisto-tools/painotettavatoppiaineetlukiossa-koodiurit))
        painokerroin (get koodiuri :painokerroin)]
    (map #(assoc {} :koodiUrit {:oppiaine %}, :painokerroin painokerroin) koodisto-oppiaineet)))

(defn- complete-koodiurit
  [koodiurit-to-complete]
  (flatten
   (map #(replace-with-koodisto-oppiaineet %) koodiurit-to-complete)))

(defn- get-matching-painokerroin
  [koodiuri koodiurit-koodisto]
  (first
   (filter #(= (get-in koodiuri [:koodiUrit :oppiaine]) (get-in % [:koodiUrit :oppiaine])) koodiurit-koodisto)))

(defn- get-painokerroin-or-use-current
  [koodiuri koodiurit-koodisto]
  (assoc koodiuri :painokerroin (get (or (get-matching-painokerroin koodiuri koodiurit-koodisto) koodiuri) :painokerroin)))

(defn- get-missing-koodiurit
  [koodiurit-koodisto koodiurit-with-painokertoimet]
  (flatten
   (seq (s/difference (set koodiurit-koodisto) (set koodiurit-with-painokertoimet)))))

(defn complete-painotetut-lukioarvosanat-kaikki
  [koodiurit]
  (let [koodiurit-to-complete (get-koodiurit-to-complete koodiurit)
        koodiurit-koodisto (get-koodisto-koodiurit koodiurit koodiurit-to-complete)
        koodiurit-completed (complete-koodiurit koodiurit-to-complete)
        koodiurit-with-painokertoimet (map #(get-painokerroin-or-use-current % koodiurit-koodisto) koodiurit-completed)
        koodiurit-to-add (get-missing-koodiurit koodiurit-koodisto koodiurit-with-painokertoimet)]
    (vec (flatten (conj koodiurit-with-painokertoimet koodiurit-to-add)))))

(defn- complete-painotetut-lukioarvosanat-if-exists
  [hakukohde]
  (if-let [painotetut-arvosanat (get-in hakukohde [:metadata :hakukohteenLinja :painotetutArvosanat])]
    (assoc-in hakukohde [:metadata :hakukohteenLinja :painotetutArvosanatOppiaineittain]
               (complete-painotetut-lukioarvosanat-kaikki painotetut-arvosanat))
    hakukohde))

(defn- odw-alempi-kk-aste?
  [koulutus-koodi-uri]
  (str/starts-with? koulutus-koodi-uri "koulutus_6"))

(defn- odw-ylempi-kk-aste?
  [koulutus-koodi-uri]
  (str/starts-with? koulutus-koodi-uri "koulutus_7"))

(defn- jatkotutkinto?
  [koulutus-koodi-uri]
  (str/starts-with? koulutus-koodi-uri "koulutus_8"))

(defn siirtohaku?
  [haku]
  (str/starts-with? (:hakutapaKoodiUri haku) "hakutapa_05"))

(defn- lääkis?
  [koulutus-koodi-uri]
  (let [matching-start-withs ["koulutus_772100" "koulutus_772101" "koulutus_772200" "koulutus_772201" "koulutus_772300" "koulutus_772301"]]
    (boolean (some #(str/starts-with? koulutus-koodi-uri %) matching-start-withs))))

(defn- get-odw-kk-tutkinnon-taso-sykli
  [alempi-kk-aste ylempi-kk-aste jatkotutkinto siirtohaku lääkis hakukohde-oid]
  (log/info (str "get-odw-kk-tutkinnon-taso-sykli | hakukohde-oid: " hakukohde-oid " | alempi-kk-aste: " alempi-kk-aste " | ylempi-kk-aste: " ylempi-kk-aste " | jatkotutkinto: " jatkotutkinto " | siirtohaku: " siirtohaku " | lääkis: " lääkis))
  (cond
    (true? jatkotutkinto) 5
    (true? siirtohaku) (cond
                         (and (true? alempi-kk-aste) (false? ylempi-kk-aste)) 2
                         (and (true? alempi-kk-aste) (true? ylempi-kk-aste)) 2
                         (and (false? alempi-kk-aste) (true? ylempi-kk-aste) (true? lääkis)) 2
                         (and (false? alempi-kk-aste) (true? ylempi-kk-aste)) 4
                         :else -1)
    (and (true? alempi-kk-aste) (false? ylempi-kk-aste)) 1
    (and (true? alempi-kk-aste) (true? ylempi-kk-aste)) 1
    (and (false? alempi-kk-aste) (true? ylempi-kk-aste) (true? lääkis)) 1
    (and (false? alempi-kk-aste) (true? ylempi-kk-aste)) 3
    :else 6))

(defn- get-odw-kk-tutkinnon-taso
  [alempi-kk-aste ylempi-kk-aste jatkotutkinto hakukohde-oid]
  (log/info (str "get-odw-kk-tutkinnon-taso | hakukohde-oid: " hakukohde-oid " | alempi-kk-aste: " alempi-kk-aste " | ylempi-kk-aste: " ylempi-kk-aste " | jatkotutkinto: " jatkotutkinto))
  (cond
    (and (true? alempi-kk-aste) (false? ylempi-kk-aste)) 1
    (and (false? alempi-kk-aste) (true? ylempi-kk-aste)) 2
    (and (true? alempi-kk-aste) (true? ylempi-kk-aste)) 3
    (true? jatkotutkinto) 4
    :else 5))

(defn- assoc-jarjestaa-urheilijan-ammatillista-koulutusta [hakukohde juak?]
  (if (nil? juak?)
    hakukohde
    (assoc hakukohde :jarjestaaUrheilijanAmmKoulutusta juak?)))

(defn- assoc-pistehistoria [hakukohde pistehistoria]
  (if (nil? pistehistoria)
    hakukohde
    (assoc-in hakukohde [:metadata :pistehistoria] pistehistoria)))

(defn- assoc-odw-kk-tasot
  [hakukohde haku koulutus]
  (if (korkeakoulutus? koulutus)
    (let [koulutusKoodiUrit (get koulutus :koulutuksetKoodiUri)
          alempi-kk-aste (and (boolean (some odw-alempi-kk-aste? koulutusKoodiUrit)) (johtaa-tutkintoon? koulutus))
          ylempi-kk-aste (and (boolean (some odw-ylempi-kk-aste? koulutusKoodiUrit)) (johtaa-tutkintoon? koulutus))
          jatkotutkinto (and (boolean (some jatkotutkinto? koulutusKoodiUrit)) (johtaa-tutkintoon? koulutus))
          siirtohaku (siirtohaku? haku)
          lääkis (boolean (some lääkis? koulutusKoodiUrit))
          kk-tutkinnon-taso (get-odw-kk-tutkinnon-taso alempi-kk-aste ylempi-kk-aste jatkotutkinto (:oid hakukohde))
          kk-tutkinnon-taso-sykli (get-odw-kk-tutkinnon-taso-sykli alempi-kk-aste ylempi-kk-aste jatkotutkinto siirtohaku lääkis (:oid hakukohde))]
      (-> hakukohde
          (assoc :odwKkTasot {:alempiKkAste         alempi-kk-aste
                              :ylempiKkAste         ylempi-kk-aste
                              :kkTutkinnonTaso      kk-tutkinnon-taso
                              :kkTutkinnonTasoSykli kk-tutkinnon-taso-sykli})))
    hakukohde))

(defn get-koulutusasteet
  [koulutus]
  (mapcat koulutusasteet (:koulutuksetKoodiUri koulutus)))

(defn create-index-entry
  [oid execution-id]
  (let [hakukohde-from-kouta (kouta-backend/get-hakukohde-with-cache oid execution-id)]
    (if (not-poistettu? hakukohde-from-kouta)
      (let [hakukohde (-> hakukohde-from-kouta
                          (assoc-hakukohde-nimi-as-esitysnimi)
                          (complete-painotetut-lukioarvosanat-if-exists)
                          (common/complete-entry))
            haku (kouta-backend/get-haku-with-cache (:hakuOid hakukohde) execution-id)
            toteutus (kouta-backend/get-toteutus-with-cache (:toteutusOid hakukohde) execution-id)
            koulutus (kouta-backend/get-koulutus-with-cache (:koulutusOid toteutus) execution-id)
            sora-kuvaus (kouta-backend/get-sorakuvaus-with-cache (:sorakuvausId koulutus) execution-id)
            valintaperusteId (:valintaperusteId hakukohde)
            valintaperuste (when-not (str/blank? valintaperusteId)
                             (kouta-backend/get-valintaperuste-with-cache valintaperusteId execution-id))
            pistehistoria (kouta-backend/get-pistehistoria-for-hakukohde hakukohde-from-kouta execution-id)]
        (indexable/->index-entry-with-forwarded-data oid
                                                     (-> hakukohde
                                                         (assoc-koulutustyyppi-path koulutus (:metadata toteutus))
                                                         (assoc-yps haku koulutus)
                                                         (assoc :koulutustyyppi (:koulutustyyppi koulutus))
                                                         (set-hakukohde-tila-by-related-haku haku)
                                                         (assoc :hakuNimi (:nimi haku))
                                                         (assoc-hakuajat haku)
                                                         (assoc-sora-data sora-kuvaus)
                                                         (assoc-onko-harkinnanvarainen-koulutus toteutus koulutus)
                                                         (assoc-koulutustyypit toteutus koulutus)
                                                         (assoc-toteutus toteutus)
                                                         (assoc-valintaperuste valintaperuste)
                                                         (assoc-hakulomake-linkki haku)
                                                         (assoc-paatelty-alkamiskausi-for-hakukohde hakukohde-from-kouta haku toteutus)
                                                         (assoc-odw-kk-tasot haku koulutus)
                                                         (assoc-jarjestaa-urheilijan-ammatillista-koulutusta (get-in hakukohde [:metadata :jarjestaaUrheilijanAmmKoulutusta]))
                                                         (assoc-pistehistoria pistehistoria)
                                                         (assoc :johtaaTutkintoon (johtaa-tutkintoon? koulutus))
                                                         (assoc :hakutapaKoodiUri (:hakutapaKoodiUri haku))
                                                         (assoc :opetuskieliKoodiUrit (get-in toteutus [:metadata :opetus :opetuskieliKoodiUrit]))
                                                         (assoc :koulutusasteKoodiUrit (get-koulutusasteet koulutus))
                                                         (common/localize-dates)) hakukohde))
      (indexable/->delete-entry-with-forwarded-data oid hakukohde-from-kouta))))

(defn do-index
  [oids execution-id]
  (indexable/do-index index-name oids create-index-entry execution-id))

(defn get-from-index
  [oid]
  (indexable/get index-name oid))
