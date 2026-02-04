(ns mocks.kouta-external-mocks
  (:require
   [clj-log.access-log]
   [clj-test-utils.elasticsearch-docker-utils :as ed-utils]
   [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
   [kouta-indeksoija-service.util.time :as time]
   [mocks.export-elastic-data :refer [export-elastic-data]])
  (:import (java.time LocalDateTime)))

(defonce OphOid             "1.2.246.562.10.00000000001")
(defonce ParentOid          "1.2.246.562.10.594252633210")
(defonce ChildOid           "1.2.246.562.10.81934895871")
(defonce EvilChild          "1.2.246.562.10.66634895871")
(defonce EvilCousin         "1.2.246.562.10.66634895666")
(defonce LonelyOid          "1.2.246.562.10.99999999999")

(defonce sorakuvausId1     "e17773b2-f5a0-418d-a49f-34578c4b3625")
(defonce sorakuvausId2     "171c3d2c-a43e-4155-a68f-f5c9816f3154")
(defonce ataruId1          "dcd38a87-912e-4e91-8840-99c7e242dd53")
(defonce ataruId2          "dcd38a87-912e-4e91-8840-99c7e242dd54")
(defonce valintaPerusteId1 "fa7fcb96-3f80-4162-8d19-5b74731cf90c")
(defonce valintaPerusteId2 "171c3d2c-a43e-4155-a68f-f5c9816f3154")
(defonce valintaPerusteId3 "db8acf4f-6e29-409d-93a4-06000fa9a4cd")

(defonce koulutusOid1   "1.2.246.562.13.00000000000000000001")
(defonce koulutusOid2   "1.2.246.562.13.00000000000000000002")
(defonce koulutusOid3   "1.2.246.562.13.00000000000000000003")
(defonce koulutusOid4   "1.2.246.562.13.00000000000000000004")
(defonce koulutusOid5   "1.2.246.562.13.00000000000000000005")
(defonce koulutusOid6   "1.2.246.562.13.00000000000000000006")
(defonce koulutusOid7   "1.2.246.562.13.00000000000000000007")

(defonce toteutusOid1   "1.2.246.562.17.00000000000000000001")
(defonce toteutusOid2   "1.2.246.562.17.00000000000000000002")

(defonce hakuOid1       "1.2.246.562.29.00000000000000000001")
(defonce hakuOid2       "1.2.246.562.29.00000000000000000002")
(defonce hakuOid3       "1.2.246.562.29.00000000000000000003")
(defonce hakuOid4       "1.2.246.562.29.00000000000000000004")
(defonce hakuOid5       "1.2.246.562.29.00000000000000000005")
(defonce hakuOid6       "1.2.246.562.29.00000000000000000006")

(defonce hakukohdeOid1    "1.2.246.562.20.00000000000000000001")
(defonce hakukohdeOid2    "1.2.246.562.20.00000000000000000002")
(defonce hakukohdeOid3    "1.2.246.562.20.00000000000000000003")

(defn- constant-millis []
  (-> (LocalDateTime/parse "2023-10-11T01:00:00")
      (.atZone time/timezone-fi)
      (.toInstant)
      (.toEpochMilli)))

(defn generate []
  (ed-utils/start-elasticsearch)
  (fixture/init)
  (fixture/add-sorakuvaus-mock sorakuvausId1 :organisaatio ChildOid)
  (fixture/add-sorakuvaus-mock sorakuvausId2 :organisaatio OphOid)

  (fixture/add-koulutus-mock koulutusOid1 :tila "julkaistu" :organisaatio ChildOid :sorakuvausId sorakuvausId1)
  (fixture/add-koulutus-mock koulutusOid2 :tila "julkaistu" :organisaatio OphOid :sorakuvausId sorakuvausId1 :julkinen true)
  (fixture/add-koulutus-mock koulutusOid3 :tila "julkaistu" :organisaatio LonelyOid :sorakuvausId sorakuvausId1 :julkinen true)
  (fixture/add-koulutus-mock koulutusOid4 :tila "julkaistu" :organisaatio LonelyOid :sorakuvausId sorakuvausId1 :tarjoajat [ChildOid])
  (fixture/add-koulutus-mock koulutusOid5 :tila "julkaistu" :organisaatio ChildOid :koulutustyyppi "amm-muu" :metadata fixture/amm-muu-koulutus-metadata)
  (fixture/add-koulutus-mock koulutusOid6 :tila "julkaistu" :organisaatio ChildOid :koulutustyyppi "aikuisten-perusopetus" :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock koulutusOid7 :tila "julkaistu" :organisaatio ChildOid :koulutustyyppi "vapaa-sivistystyo-osaamismerkki" :metadata fixture/osaamismerkki-koulutus-metadata)

  (fixture/add-toteutus-mock toteutusOid1 koulutusOid1 :tila "julkaistu" :organisaatio ChildOid)
  (fixture/add-toteutus-mock toteutusOid2 koulutusOid1 :tila "julkaistu" :organisaatio LonelyOid :tarjoajat [ChildOid])

  (fixture/add-valintaperuste-mock valintaPerusteId1 :organisaatio ChildOid)
  (fixture/add-valintaperuste-mock valintaPerusteId2 :organisaatio OphOid :julkinen true)
  (fixture/add-valintaperuste-mock valintaPerusteId3 :organisaatio LonelyOid :julkinen true)

  (fixture/add-haku-mock hakuOid1 :tila "julkaistu" :organisaatio ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid2 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid3 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId2)
  (fixture/add-haku-mock hakuOid4 :tila "julkaistu" :organisaatioOid ChildOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId2)
  (fixture/add-haku-mock hakuOid5 :tila "julkaistu" :organisaatioOid ParentOid :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)
  (fixture/add-haku-mock hakuOid6 :tila "julkaistu" :organisaatioOid EvilChild :hakulomaketyyppi "ataru" :hakulomakeAtaruId ataruId1)

  (fixture/add-hakukohde-mock hakukohdeOid1 toteutusOid1 hakuOid1 :tila "julkaistu" :organisaatio ChildOid :valintaperusteId valintaPerusteId1)
  (fixture/add-hakukohde-mock hakukohdeOid2 toteutusOid2 hakuOid1 :tila "julkaistu" :organisaatio LonelyOid :valintaperusteId valintaPerusteId1)
  (fixture/add-hakukohde-mock hakukohdeOid3 toteutusOid2 hakuOid1 :tila "julkaistu" :organisaatio LonelyOid :valintaperusteId valintaPerusteId1)

  (fixture/index-oids-without-related-indices {:sorakuvaukset [sorakuvausId1 sorakuvausId2]
                                               :koulutukset [koulutusOid1 koulutusOid2 koulutusOid3 koulutusOid4 koulutusOid5 koulutusOid6 koulutusOid7]
                                               :toteutukset [toteutusOid1 toteutusOid2]
                                               :haut [hakuOid1 hakuOid2 hakuOid3 hakuOid4 hakuOid5 hakuOid6]
                                               :valintaperusteet [valintaPerusteId1 valintaPerusteId2 valintaPerusteId3]
                                               :hakukohteet [hakukohdeOid1 hakukohdeOid2 hakukohdeOid3]
                                               :oppilaitokset [ChildOid EvilChild LonelyOid]})
  (export-elastic-data "kouta-external")
  (ed-utils/stop-elasticsearch))

(defn -main []
  (with-redefs [time/current-time-millis constant-millis]
    (generate)))
