(ns mocks.kouta-internal-mocks
  (:require [clj-log.access-log]
            [clj-test-utils.elasticsearch-docker-utils :as ed-utils]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture :refer [default-toteutus-map]]
            [mocks.export-elastic-data :refer [export-elastic-data]]))

(defonce OphOid             "1.2.246.562.10.00000000001")
(defonce ParentOid          "1.2.246.562.10.594252633210")
(defonce ChildOid           "1.2.246.562.10.81934895871")
(defonce EvilChild          "1.2.246.562.10.66634895871")
(defonce GrandChildOid      "1.2.246.562.10.67603619189")
(defonce EvilCousin         "1.2.246.562.10.66634895666")

(defonce sorakuvausId      "9267884f-fba1-4b85-8bb3-3eb77440c197")

(defonce ammKoulutusOid           "1.2.246.562.13.00000000000000000001")
(defonce ammTukinnonosaOid        "1.2.246.562.13.00000000000000000002")
(defonce ammOsaamisalaOid         "1.2.246.562.13.00000000000000000003")
(defonce ammMuuOid                "1.2.246.562.13.00000000000000000004")
(defonce aikuitenPerusopetusOid   "1.2.246.562.13.00000000000000000005")
(defonce yoKoulutusOid            "1.2.246.562.13.00000000000000000006")

(defonce ammToteutusOid             "1.2.246.562.17.00000000000000000001")
(defonce ammTukinnonosaToteutusOid  "1.2.246.562.17.00000000000000000002")
(defonce ammOsaamisalaToteutusOid   "1.2.246.562.17.00000000000000000003")
(defonce yoToteutusOid              "1.2.246.562.17.00000000000000000004")

(defonce ataruId1          "dcd38a87-912e-4e91-8840-99c7e242dd53")
(defonce ataruId2          "dcd38a87-912e-4e91-8840-99c7e242dd54")

(defonce hakuOid1          "1.2.246.562.29.00000000000000000001")
(defonce hakuOid2          "1.2.246.562.29.00000000000000000002")
(defonce hakuOid3          "1.2.246.562.29.00000000000000000003")
(defonce hakuOid4          "1.2.246.562.29.00000000000000000004")
(defonce hakuOid5          "1.2.246.562.29.00000000000000000005")
(defonce hakuOid6          "1.2.246.562.29.00000000000000000006")
(defonce hakuOid7          "1.2.246.562.29.00000000000000000007")

(defonce hakukohdeOid1     "1.2.246.562.20.00000000000000000001")
(defonce hakukohdeOid2     "1.2.246.562.20.00000000000000000002")
(defonce hakukohdeOid3     "1.2.246.562.20.00000000000000000003")

(defonce valintaPerusteId1  "fa7fcb96-3f80-4162-8d19-5b74731cf90c")

(defonce default-toteutus-metadata (:metadata  default-toteutus-map))

(defn -main []
  (ed-utils/start-elasticsearch)

  (fixture/init)
  (fixture/add-sorakuvaus-mock sorakuvausId :organisaatioOid ChildOid)

  (fixture/add-koulutus-mock ammKoulutusOid :koulutustyyppi "amm" :tila "julkaistu" :organisaatioOid ChildOid :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock ammTukinnonosaOid :koulutustyyppi "amm-tutkinnon-osa" :tila "julkaistu" :organisaatioOid ChildOid :sorakuvausId sorakuvausId
                             :johtaaTutkintoon false :koulutuksetKoodiUri nil :ePerusteId nil :metadata fixture/amm-tutkinnon-osa-koulutus-metadata)
  (fixture/add-koulutus-mock ammOsaamisalaOid :koulutustyyppi "amm-osaamisala" :tila "julkaistu" :organisaatioOid ChildOid
                             :johtaaTutkintoon false :sorakuvausId sorakuvausId :metadata fixture/amm-osaamisala-koulutus-metadata)
  (fixture/add-koulutus-mock ammMuuOid :koulutustyyppi "amm-muu" :tila "julkaistu" :organisaatioOid ChildOid
                             :johtaaTutkintoon false :sorakuvausId sorakuvausId :metadata fixture/amm-muu-koulutus-metadata)
  (fixture/add-koulutus-mock aikuitenPerusopetusOid :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :organisaatioOid ChildOid
                             :johtaaTutkintoon false :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock yoKoulutusOid :tila "julkaistu" :johtaaTutkintoon true :koulutustyyppi "yo" :nimi "Korkeakoulu"
                             :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)


  (fixture/add-toteutus-mock ammToteutusOid ammKoulutusOid :tila "julkaistu" :organisaatioOid ChildOid :tarjoajat ChildOid
                             :metadata (merge default-toteutus-metadata {:tyyppi "amm"}))
  (fixture/add-toteutus-mock ammTukinnonosaToteutusOid ammTukinnonosaOid :tila "julkaistu" :organisaatioOid ChildOid :tarjoajat ChildOid
                             :metadata (merge default-toteutus-metadata {:tyyppi "amm-tutkinnon-osa"}))
  (fixture/add-toteutus-mock ammOsaamisalaToteutusOid ammOsaamisalaOid :tila "julkaistu" :organisaatioOid ChildOid :tarjoajat ChildOid
                             :metadata (merge default-toteutus-metadata {:tyyppi "amm-osaamisala"}))
  (fixture/add-toteutus-mock yoToteutusOid yoKoulutusOid :tila "julkaistu" :organisaatioOid ChildOid :tarjoajat ChildOid
                             :metadata (merge default-toteutus-metadata {:tyyppi "yo"}) :johtaaTutkintoon true)

  (fixture/add-haku-mock hakuOid1 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid2 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid3 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId2)
  (fixture/add-haku-mock hakuOid4 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId2)
  (fixture/add-haku-mock hakuOid5 :tila "julkaistu" :organisaatioOid ParentOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid6 :tila "julkaistu" :organisaatioOid EvilChild :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid7 :tila "julkaistu" :organisaatioOid ChildOid :kohdejoukkoKoodiUri "haunkohdejoukko_12#2"
                         :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1 :metadata fixture/maksullinen-kk-haku-metadata)

  (fixture/add-valintaperuste-mock valintaPerusteId1 :organisaatioOid ChildOid)
  (fixture/add-hakukohde-mock hakukohdeOid1 ammToteutusOid hakuOid1 :valintaperuste valintaPerusteId1 :organisaatioOid ChildOid :jarjestyspaikkaOid ChildOid)
  (fixture/add-hakukohde-mock hakukohdeOid2 ammToteutusOid hakuOid1 :valintaperuste valintaPerusteId1 :organisaatioOid GrandChildOid :jarjestyspaikkaOid OphOid)
  (fixture/add-hakukohde-mock hakukohdeOid3 yoToteutusOid hakuOid7 :valintaperuste valintaPerusteId1 :organisaatioOid ChildOid :jarjestyspaikkaOid ChildOid)
  (fixture/index-oids-without-related-indices {:koulutukset [ammKoulutusOid ammTukinnonosaOid ammOsaamisalaOid ammMuuOid aikuitenPerusopetusOid yoKoulutusOid]
                                               :toteutukset [ammToteutusOid ammTukinnonosaToteutusOid ammOsaamisalaToteutusOid yoToteutusOid]
                                               :haut [hakuOid1 hakuOid2 hakuOid3 hakuOid4 hakuOid5 hakuOid6 hakuOid7]
                                               :valintaperusteet [valintaPerusteId1]
                                               :hakukohteet [hakukohdeOid1 hakukohdeOid2 hakukohdeOid3]
                                               :oppilaitokset [ChildOid EvilChild]})
  (export-elastic-data "kouta-internal")
  (ed-utils/stop-elasticsearch))
