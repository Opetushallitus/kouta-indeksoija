(ns kouta-indeksoija-service.indexer.tools.general
  (:require [clojure.string :as string]))

(defonce Julkaistu "julkaistu")
(defonce Tallennettu "tallennettu")
(defonce Poistettu "poistettu")
(defonce Arkistoitu "arkistoitu")

(defn julkaistu?
  [entry]
  (and (not (nil? entry)) (= (:tila entry) Julkaistu)))

(defn not-poistettu?
  [entry]
  (let [tila (:tila entry)]
    (and (not (nil? entry))
         (not (nil? tila))
         (not= tila "poistettu"))))

(defn luonnos?
  [entry]
  (and (not (nil? entry)) (= (:tila entry) Tallennettu)))

(defn not-arkistoitu?
  [entry]
  (and (not (nil? entry)) (not= (:tila entry) Arkistoitu)))

(defn ammatillinen?
  [koulutus]
  (= "amm" (:koulutustyyppi koulutus)))

(defn amm-tutkinnon-osa?
  [koulutus]
  (= "amm-tutkinnon-osa" (:koulutustyyppi koulutus)))

(defn amm-osaamisala?
  [koulutus]
  (= "amm-osaamisala" (:koulutustyyppi koulutus)))

(defn amm-muu?
  [koulutus]
  (= "amm-muu" (:koulutustyyppi koulutus)))

(defn korkeakoulutus?
  [koulutus]
  (some
   #(= (:koulutustyyppi koulutus) %)
   ["yo", "amk", "amm-ope-erityisope-ja-opo" "ope-pedag-opinnot" "kk-opintokokonaisuus" "kk-opintojakso" "erikoislaakari", "erikoistumiskoulutus"]))

(defn yo?
  [koulutus]
  (= "yo" (:koulutustyyppi koulutus)))

(defn amk?
  [koulutus]
  (= "amk" (:koulutustyyppi koulutus)))

(defn lukio?
  [koulutus]
  (= "lk" (:koulutustyyppi koulutus)))

(defn tuva?
  [koulutus]
  (= "tuva" (:koulutustyyppi koulutus)))

(defn telma?
  [koulutus]
  (= "telma" (:koulutustyyppi koulutus)))

(defn vapaa-sivistystyo-opistovuosi?
  [koulutus]
  (= "vapaa-sivistystyo-opistovuosi" (:koulutustyyppi koulutus)))

(defn vapaa-sivistystyo-muu?
  [koulutus]
  (= "vapaa-sivistystyo-muu" (:koulutustyyppi koulutus)))

(defn vapaa-sivistystyo-osaamismerkki?
  [koulutus]
  (= "vapaa-sivistystyo-osaamismerkki" (:koulutustyyppi koulutus)))

(defn amm-ope-erityisope-ja-opo?
  [koulutus]
  (= "amm-ope-erityisope-ja-opo" (:koulutustyyppi koulutus)))

(defn ope-pedag-opinnot?
  [koulutus]
  (= "ope-pedag-opinnot" (:koulutustyyppi koulutus)))

(defn kk-opintojakso?
  [koulutus]
  (= "kk-opintojakso" (:koulutustyyppi koulutus)))
(defn muu-opintojakso?
  [koulutus]
  (= "muu" (:koulutustyyppi koulutus)))

(defn erikoislaakari?
  [koulutus]
  (= "erikoislaakari" (:koulutustyyppi koulutus)))

(defn kk-opintokokonaisuus?
  [koulutus]
  (= "kk-opintokokonaisuus" (:koulutustyyppi koulutus)))

(defn aikuisten-perusopetus?
  [koulutus]
  (= "aikuisten-perusopetus" (:koulutustyyppi koulutus)))

(defn erikoistumiskoulutus?
  [koulutus]
  (= "erikoistumiskoulutus" (:koulutustyyppi koulutus)))

;Toimii koulutukselle, jolla on rikastettu tai rikastamaton koulutus-koodiurit
(defn get-non-korkeakoulu-koodi-uri
  [koulutus]
  (let [koulutukset (map :koodiUri (:koulutukset koulutus))
        koulutus-koodi-urit (if (seq koulutukset) koulutukset (:koulutuksetKoodiUri koulutus))]
    (when (not (korkeakoulutus? koulutus))
      (first koulutus-koodi-urit)))) ;Ainoastaan korkeakoulutuksilla voi olla useampi kuin yksi koulutusKoodi

(defn asiasana->lng-value-map
  [asiasanat]
  (map (fn [a] {(keyword (:kieli a)) (:arvo a)}) asiasanat))

(defn set-hakukohde-tila-by-related-haku
  [hakukohde haku]
  (if (and (julkaistu? hakukohde) (not (nil? haku)) (not (nil? (:tila haku))) (not (julkaistu? haku)))
    (assoc hakukohde :tila (:tila haku))
    hakukohde))

(defn koodiuri-wo-version [koodiuri]
  (string/replace koodiuri #"#\w+" ""))

(defn remove-version-from-koodiuri
  [entity path-to-koodiuri]
  (let [koodiuri (get-in entity path-to-koodiuri)]
    (if (not (nil? koodiuri))
      (assoc-in entity
                path-to-koodiuri
                (koodiuri-wo-version koodiuri))
      entity)))

;esim. pelastusalan koulutuksille syötetään tiedot käsin, koska ei ole ePerustetta
(defonce koulutus-koodit-without-eperuste ["koulutus_381501" "koulutus_381502" "koulutus_381503" "koulutus_381521"])

(defn amm-koulutus-with-eperuste? [koulutus]
  (if (and koulutus (ammatillinen? koulutus))
    (when-let [koulutus-koodiuri (get-non-korkeakoulu-koodi-uri koulutus)]
      (boolean (not-any? #(string/starts-with? koulutus-koodiuri %) koulutus-koodit-without-eperuste)))
    false))
