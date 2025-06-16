(ns mocks.konfo-backend-mocks
  (:require [clj-log.access-log]
            [clj-test-utils.elasticsearch-docker-utils :as ed-utils]
            [kouta-indeksoija-service.fixture.common-oids :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.test-tools :refer [set-fixed-time]]
            [mocks.export-elastic-data :refer [export-elastic-data]]))

(defn -main []
  (ed-utils/start-elasticsearch)
  (set-fixed-time "2023-10-11T01:00:00")
  (fixture/init)
  (fixture/add-sorakuvaus-mock sorakuvausId :tila "julkaistu" :nimi "Kiva SORA-kuvaus")

  (fixture/add-koulutus-mock koulutusOid1 :koulutustyyppi "amm" :tila "julkaistu" :nimi "Traktorialan koulutus" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock koulutusOid2 :koulutustyyppi "amm" :tila "julkaistu" :nimi "Hevosalan koulutus" :modified "2018-05-05T12:02:23" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock koulutusOid3 :koulutustyyppi "amm" :tila "julkaistu" :nimi "ICT esiopinnot" :muokkaaja "1.2.246.562.24.55555555555" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock koulutusOid4 :tila "arkistoitu" :nimi "Tietojenkäsittelytieteen perusopinnot" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock koulutusOid5 :tila "tallennettu" :nimi "Tietojenkäsittelytieteen perusopinnot" :sorakuvausId sorakuvausId :julkinen true :esikatselu true)
  (fixture/add-koulutus-mock koulutusOid6 :tila "tallennettu" :koulutustyyppi "yo" :nimi "Diplomi-insinööri" :sorakuvausId sorakuvausId :metadata fixture/yo-koulutus-metadata :esikatselu false)
  (fixture/add-koulutus-mock lukio-oid :tila "julkaistu" :koulutustyyppi "lk" :nimi "Lukio" :metadata fixture/lukio-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock kk-koulutus-oid :tila "julkaistu" :koulutustyyppi "yo" :nimi "Korkeakoulu" :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock osaamismerkki-koulutus-oid :tila "julkaistu" :koulutustyyppi "vapaa-sivistystyo-osaamismerkki" :nimi "Osaamismerkki" :metadata fixture/osaamismerkki-koulutus-metadata)

  ;; Punkaharjun ja Helsingin yliopistoihin kiinnitetyt koulutukset
  (fixture/add-koulutus-mock traktoriala-oid :koulutustyyppi "amm" :tila "julkaistu" :nimi "Traktorialan koulutus" :tarjoajat [punkaharjun-yliopisto helsingin-yliopisto] :metadata (merge fixture/koulutus-metatieto {:luokittelutermit ["ohjaaja"]}) :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock hevosala-oid :koulutustyyppi "amm" :tila "julkaistu" :nimi "Hevosalan koulutus" :modified "2018-05-05T12:02:23" :tarjoajat [punkaharjun-yliopisto helsingin-yliopisto] :metadata (merge fixture/koulutus-metatieto {:luokittelutermit ["ohjaaja"]}) :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock traktoriala-oid2 :koulutustyyppi "amm" :tila "julkaistu" :nimi "Traktorialan koulutus" :metadata fixture/koulutus-metatieto :sorakuvausId sorakuvausId :tarjoajat [])
  (fixture/add-koulutus-mock hevostutkinnon-osa-oid :koulutustyyppi "amm-tutkinnon-osa" :koulutuksetKoodiUri nil :ePerusteId nil :tila "julkaistu" :johtaaTutkintoon false :nimi "Hevosalan tutkinnon osa koulutus" :tarjoajat [punkaharjun-yliopisto] :sorakuvausId sorakuvausId :metadata fixture/amm-tutkinnon-osa-koulutus-metadata)
  (fixture/add-koulutus-mock hevososaamisala-oid :koulutustyyppi "amm-osaamisala" :tila "julkaistu" :johtaaTutkintoon false :nimi "Hevosalan osaamisala koulutus" :tarjoajat [punkaharjun-yliopisto] :metadata fixture/amm-osaamisala-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock yo-koulutus-oid :tila "julkaistu" :koulutustyyppi "yo" :nimi "Arkkitehti" :tarjoajat [punkaharjun-yliopisto] :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock amk-oid :tila "julkaistu" :koulutustyyppi "amk" :nimi "Artenomi" :tarjoajat [punkaharjun-yliopisto] :metadata (merge fixture/amk-koulutus-metadata {:luokittelutermit ["ohjaaja"]}) :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock lukio-oid2 :tila "julkaistu" :koulutustyyppi "lk" :nimi "Lukio" :tarjoajat [punkaharjun-yliopisto] :metadata fixture/lukio-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock amm-muu-oid :tila "julkaistu" :koulutustyyppi "amm-muu" :nimi "Muu ammatillinen" :tarjoajat [punkaharjun-yliopisto] :metadata fixture/amm-muu-koulutus-metadata :sorakuvausId sorakuvausId)

  (fixture/add-koulutus-mock puutarha-koulutus-oid :koulutustyyppi "amm" :tila "julkaistu" :nimi "Puutarha-koulutus" :modified "2025-05-05T12:02:23" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/koulutus-metatieto :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock tuva-koulutus-oid :koulutustyyppi "tuva" :tila "julkaistu" :nimi "Tuva-koulutus" :modified "2025-05-05T12:02:23" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/tuva-koulutus-metadata)

  (fixture/add-koulutus-mock koulutusOid7 :koulutustyyppi "amm" :tila "julkaistu" :nimi "Amm koulutus jolla luokittelutermi muttei toteutusta" :modified "2018-05-05T12:02:23" :metadata (merge fixture/koulutus-metatieto {:luokittelutermit ["ohjaaja"]}) :sorakuvausId sorakuvausId)

  (fixture/add-koulutus-mock aakkostus-koulutus-oid1 :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :nimi "Aakkosissa ensimmäinen"   :tarjoajat aakkostus-oppilaitos-oid1
                             :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock aakkostus-koulutus-oid2 :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :nimi "Aakkosissa toinen"        :tarjoajat aakkostus-oppilaitos-oid2
                             :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock aakkostus-koulutus-oid3 :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :nimi "Aakkosissa vasta kolmas"  :tarjoajat aakkostus-oppilaitos-oid3
                             :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock aakkostus-koulutus-oid4 :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :nimi "Aakkosissa vasta neljäs"  :tarjoajat aakkostus-oppilaitos-oid4
                             :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)
  (fixture/add-koulutus-mock aakkostus-koulutus-oid5 :koulutustyyppi "aikuisten-perusopetus" :tila "julkaistu" :nimi "Aakkosissa viidentenä"    :tarjoajat aakkostus-oppilaitos-oid5
                             :sorakuvausId sorakuvausId :metadata fixture/aikuisten-perusopetus-koulutus-metadata)

  (fixture/add-koulutus-mock keyword-koulutus-oid1  :koulutustyyppi "yo"  :tila "julkaistu" :tarjoajat oppilaitosOid1 :nimi "Lääketieteen koulutus" :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid2  :koulutustyyppi "yo"  :tila "julkaistu" :tarjoajat oppilaitosOid2 :nimi "Humanistinen koulutus" :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid3  :koulutustyyppi "yo"  :tila "julkaistu" :tarjoajat oppilaitosOid3 :nimi "Tietojenkäsittelytieteen koulutus" :metadata fixture/yo-koulutus-metadata :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid4  :koulutustyyppi "amm" :tila "julkaistu" :tarjoajat oppilaitosOid4 :nimi "Automaatiotekniikka (ylempi AMK :sorakuvausId sorakuvaus-id)" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid5  :koulutustyyppi "amm" :tila "julkaistu" :tarjoajat oppilaitosOid5 :nimi "Muusikon koulutus" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid6  :koulutustyyppi "amm" :tile "julkaistu" :tarjoajat oppilaitosOid6 :nimi "Sosiaali- ja terveysalan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid7  :koulutustyyppi "amm" :tile "julkaistu" :nimi "Maanmittausalan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid8  :koulutustyyppi "amm" :tile "julkaistu" :nimi "Pintakäsittelyalan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid9  :koulutustyyppi "amm" :tile "julkaistu" :nimi "Puhtaus- ja kiinteistöpalvelualan ammattitutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid10 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Puhevammaisten tulkkauksen erikoisammattitutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid11 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Hius- ja kauneudenhoitoalan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid12 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Autoalan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid13 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Elintarvikealan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid14 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Eläintenhoidon ammattitutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid15 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Tieto- ja viestintätekniikan ammattitutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid16 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Tanssialan perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid17 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Hevostalouden perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid17 :koulutustyyppi "amm" :tile "julkaistu" :nimi "Hevostalouden perustutkinto" :sorakuvausId sorakuvausId)
  (fixture/add-koulutus-mock keyword-koulutus-oid18 :koulutustyyppi "amk" :tile "julkaistu" :nimi "Moottorialan perustutkinto" :sorakuvausId sorakuvausId)

  (fixture/add-toteutus-mock toteutusOid1 koulutusOid3 :tila "julkaistu" :nimi "Traktorialan alkuopinnot" :metadata fixture/amm-toteutus-metatieto)
  (fixture/add-toteutus-mock toteutusOid2 koulutusOid3 :tila "julkaistu" :nimi "Pneumatiikan alkuopinnot" :metadata fixture/amm-toteutus-metatieto)
  (fixture/add-toteutus-mock toteutusOid3 koulutusOid3 :tila "julkaistu" :nimi "Traktorialan alkuopinnot" :modified "2018-05-05T12:02:23" :muokkaaja "1.2.246.562.24.55555555555" :metadata fixture/amm-toteutus-metatieto)
  (fixture/add-toteutus-mock toteutusOid4 koulutusOid3 :tila "arkistoitu" :nimi "Traktorialan alkuopinnot" :modified "2018-06-05T12:02:23" :esikatselu true)
  (fixture/add-toteutus-mock toteutusOid5 koulutusOid3 :tila "tallennettu" :nimi "Traktorialan alkuopinnot" :modified "2018-06-05T12:02:23" :esikatselu true)
  (fixture/add-toteutus-mock toteutusOid6 koulutusOid3 :tila "tallennettu" :nimi "Traktorialan alkuopinnot" :modified "2018-06-05T12:02:23" :esikatselu false)

  (fixture/add-toteutus-mock lukio-toteutus-oid lukio-oid     :tila "julkaistu" :nimi "Lukio" :modified "2018-06-05T12:02:23" :metadata fixture/lk-toteutus-metadata)
  (fixture/add-toteutus-mock kk-toteutus-oid    kk-koulutus-oid     :tila "julkaistu" :nimi "Korkeakoulu" :modified "2018-06-05T12:02:23" :metadata fixture/yo-toteutus-metatieto)
  (fixture/add-toteutus-mock osaamismerkki-toteutus-oid osaamismerkki-koulutus-oid :tila "julkaistu" :nimi "Osaamismerkki" :modified "2018-06-05T12:02:23" :metadata fixture/osaamismerkki-toteutus-metatieto)

  ;; Punkaharjun ja Helsingin yliopistoihin kiinnitetyt toteutukset
  (fixture/add-toteutus-mock ponikoulu-oid hevosala-oid               :koulutustyyppi "amm"             :tila "julkaistu" :nimi "Ponikoulu" :tarjoajat [punkaharjun-toimipiste-2] :metadata fixture/oletus-toteutus-metatieto)
  (fixture/add-toteutus-mock valtrakoulu-oid traktoriala-oid          :koulutustyyppi "amm"             :tila "julkaistu" :nimi "Valtrakoulutus" :tarjoajat [punkaharjun-toimipiste-2] :metadata fixture/oletus-toteutus-metatieto)
  (fixture/add-toteutus-mock massikkakoulu-oid traktoriala-oid        :koulutustyyppi "amk"             :tila "julkaistu" :nimi "Massikkakoulutus" :tarjoajat [helsingin-toimipiste] :metadata fixture/amk-toteutus-metatieto)
  (fixture/add-toteutus-mock zetorkoulu-oid traktoriala-oid2          :koulutustyyppi "amm"             :tila "julkaistu" :nimi "Zetorkoulutus" :tarjoajat [punkaharjun-toimipiste-2] :metadata fixture/oletus-toteutus-metatieto)
  (fixture/add-toteutus-mock poniosatoteutus-oid hevososaamisala-oid  :koulutustyyppi "amm-osaamisala"  :tila "julkaistu" :nimi "Ponikoulu tutkinnon osa" :tarjoajat [punkaharjun-toimipiste-2] :metadata fixture/amm-osaamisala-toteutus-metatieto)

  (fixture/add-toteutus-mock puutarha-ala-toteutus-erityisopetuksena-oid puutarha-koulutus-oid :koulutustyyppi "amm" :tila "julkaistu" :nimi "Puutarha-ala erityisopetuksena" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/amm-erityisopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock puutarha-ala-toteutus-oid puutarha-koulutus-oid :koulutustyyppi "amm" :tila "julkaistu" :nimi "Puutarha-ala tavallinen" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/amm-toteutus-metatieto)
  (fixture/add-toteutus-mock tuva-toteutus-erityisopetuksena-oid tuva-koulutus-oid :koulutustyyppi "tuva" :tila "julkaistu" :nimi "Tuva erityisopetuksena" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/tuva-erityisopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock tuva-toteutus-oid tuva-koulutus-oid :koulutustyyppi "tuva" :tila "julkaistu" :nimi "Tuva tavallinen" :tarjoajat [oppilaitosOid2]
                             :metadata fixture/tuva-toteutus-metatieto)

  (fixture/add-toteutus-mock aakkostus-toteutus-oid1 aakkostus-koulutus-oid1 :tila "julkaistu" :nimi "Aakkosissa ensimmäinen toteutus" :tarjoajat aakkostus-oppilaitos-oid1 :metadata fixture/aikuisten-perusopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock aakkostus-toteutus-oid2 aakkostus-koulutus-oid2 :tila "julkaistu" :nimi "Aakkosissa toinen toteutus" :tarjoajat aakkostus-oppilaitos-oid2 :metadata fixture/aikuisten-perusopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock aakkostus-toteutus-oid3 aakkostus-koulutus-oid3 :tila "julkaistu" :nimi "Aakkosissa vasta kolmas toteutus" :tarjoajat aakkostus-oppilaitos-oid3 :metadata fixture/aikuisten-perusopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock aakkostus-toteutus-oid4 aakkostus-koulutus-oid4 :tila "julkaistu" :nimi "Aakkosissa vasta neljäs toteutus" :tarjoajat aakkostus-oppilaitos-oid4 :metadata fixture/aikuisten-perusopetus-toteutus-metatieto)
  (fixture/add-toteutus-mock aakkostus-toteutus-oid5 aakkostus-koulutus-oid5 :tila "julkaistu" :nimi "Aakkosissa viides toteutus" :tarjoajat aakkostus-oppilaitos-oid5 :metadata fixture/aikuisten-perusopetus-toteutus-metatieto)

  (fixture/add-toteutus-mock keyword-toteutus-oid1 keyword-koulutus-oid1  :koulutustyyppi "yo"  :tila "julkaistu" :tarjoajat oppilaitosOid1 :metadata {:tyyppi "yo" :ammattinimikkeet [{:kieli "fi" :arvo "lääkäri"} {:kieli "fi" :arvo "esimies"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid2 keyword-koulutus-oid2  :koulutustyyppi "yo"  :tila "julkaistu" :tarjoajat oppilaitosOid2 :metadata {:tyyppi "yo" :ammattinimikkeet [{:kieli "fi" :arvo "psykologi"}] :asiasanat [{:kieli "fi" :arvo "ammattikorkeakoulu"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid3 keyword-koulutus-oid4  :koulutustyyppi "amm" :tila "julkaistu" :tarjoajat oppilaitosOid4 :metadata {:tyyppi "amm" :ammattinimikkeet [{:kieli "fi" :arvo "automaatioinsinööri"}] :asiasanat [{:kieli "fi" :arvo "ammattioppilaitos"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid4 keyword-koulutus-oid5  :koulutustyyppi "amm" :tila "julkaistu" :tarjoajat oppilaitosOid5 :metadata {:tyyppi "amm" :asiasanat [{:kieli "fi" :arvo "musiikkioppilaitokset"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid5 keyword-koulutus-oid8  :koulutustyyppi "amm" :tila "julkaistu" :metadata {:tyyppi "amm" :ammattinimikkeet [{:kieli "fi" :arvo "maalari"} {:kieli "fi" :arvo "merimies"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid6 keyword-koulutus-oid12 :koulutustyyppi "amm" :tila "julkaistu" :metadata {:tyyppi "amm" :ammattinimikkeet [{:kieli "fi" :arvo "automaalari"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid7 keyword-koulutus-oid14 :koulutustyyppi "amm" :tila "julkaistu" :metadata {:tyyppi "amm" :ammattinimikkeet [{:kieli "fi" :arvo "eläintenhoitaja"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid8 keyword-koulutus-oid17 :koulutustyyppi "amm" :tila "julkaistu" :metadata {:tyyppi "amm" :ammattinimikkeet [{:kieli "fi" :arvo "hevostenhoitaja"} {:kieli "fi" :arvo "seppä"}]})
  (fixture/add-toteutus-mock keyword-toteutus-oid9 keyword-koulutus-oid18 :koulutustyyppi "amk" :tila "julkaistu" :metadata {:tyyppi "amk" :ammattinimikkeet [{:kieli "fi" :arvo "virittäjä"}]})

  (fixture/add-haku-mock hakuOid1     :tila "julkaistu"   :nimi "Hevoshaku" :muokkaaja "1.2.246.562.24.62301161440" :hakutapaKoodiUri "hakutapa_03#1")
  (fixture/add-haku-mock hakuOid2     :tila "julkaistu"   :nimi "Yhteishaku" :muokkaaja "1.2.246.562.24.62301161440" :hakutapaKoodiUri "hakutapa_01#1")
  (fixture/add-haku-mock hakuOid3     :tila "julkaistu"   :nimi "Jatkuva haku" :modified "2018-05-05T12:02:23" :muokkaaja "1.2.246.562.24.55555555555")
  (fixture/add-haku-mock hakuOid4     :tila "arkistoitu"  :nimi "Jatkuva haku" :modified "2018-06-05T12:02:23")
  (fixture/add-haku-mock hakuOid5     :tila "tallennettu" :nimi "Jatkuva haku" :modified "2018-06-05T12:02:23" :esikatselu false)
  (fixture/add-haku-mock kk-haku-oid  :tila "julkaistu"   :nimi "KK-haku" :muokkaaja "1.2.246.562.24.62301161440" :hakutapaKoodiUri "hakutapa_01#1")

  (fixture/add-valintaperuste-mock valintaperusteId1 :tila "julkaistu" :nimi "Valintaperustekuvaus" :organisaatio oppilaitos-oid3)
  (fixture/add-valintaperuste-mock valintaperusteId2 :tila "julkaistu" :nimi "Valintaperuste" :muokkaaja "1.2.246.562.24.62301161440")
  (fixture/add-valintaperuste-mock valintaperusteId3 :tila "julkaistu" :nimi "Kiva valintaperustekuvaus" :modified "2018-05-05T12:02:23" :muokkaaja "1.2.246.562.24.55555555555")
  (fixture/add-valintaperuste-mock valintaperusteId4 :tila "arkistoitu" :nimi "Kiva valintaperustekuvaus" :modified "2018-06-05T12:02:23")
  (fixture/add-valintaperuste-mock valintaperusteId5 :tila "tallennettu" :esikatselu false :nimi "Kiva valintaperustekuvaus" :modified "2018-06-05T12:02:23")
  (fixture/add-valintaperuste-mock valintaperusteId6 :tila "tallennettu" :esikatselu true :nimi "Kiva valintaperustekuvaus" :modified "2018-06-05T12:02:23")

  (fixture/add-hakukohde-mock hakukohdeOid1 toteutusOid1 hakuOid1 :tila "julkaistu" :esitysnimi "Hakukohde" :valintaperuste valintaperusteId1 :organisaatio oppilaitos-oid3)
  (fixture/add-hakukohde-mock hakukohdeOid2 toteutusOid4 hakuOid1 :tila "julkaistu" :esitysnimi "Hakukohde" :valintaperuste valintaperusteId5)
  (fixture/add-hakukohde-mock hakukohdeOid3 toteutusOid2 hakuOid1 :tila "julkaistu" :esitysnimi "autoalan hakukohde" :valintaperuste valintaperusteId1 :modified "2018-05-05T12:02:23" :muokkaaja "1.2.246.562.24.55555555555")
  (fixture/add-hakukohde-mock hakukohdeOid4 toteutusOid5 hakuOid1 :tila "arkistoitu" :esitysnimi "Autoalan hakukohde" :valintaperuste valintaperusteId1 :modified "2018-06-05T12:02:23")
  (fixture/add-hakukohde-mock hakukohdeOid5 toteutusOid5 hakuOid1 :tila "tallennettu" :esitysnimi "Autoalan hakukohde" :valintaperuste valintaperusteId6 :modified "2018-06-05T12:02:23" :esikatselu true :hakuaikaPaattyy "2100-04-14T09:58")
  (fixture/add-hakukohde-mock hakukohdeOid6 toteutusOid5 hakuOid1 :tila "tallennettu" :esitysnimi "Autoalan hakukohde" :valintaperuste valintaperusteId6 :modified "2018-06-05T12:02:23" :esikatselu false)
  (fixture/add-hakukohde-mock hakukohdeOid7 toteutusOid1 hakuOid1 :tila "tallennettu" :nimi "Hakukohde" :organisaatio oppilaitos-oid :valintaperuste valintaperusteId1 :esikatselu false)
  (fixture/add-hakukohde-mock hakukohdeOid8 ponikoulu-oid hakuOid1 :tila "julkaistu" :nimi "ponikoulun hakukohde" :muokkaaja "1.2.246.562.24.62301161440" :hakuaikaAlkaa "2000-01-01T00:00" :hakuaikaPaattyy "2100-01-01T00:00" :valintaperuste valintaperusteId2)
  (fixture/add-hakukohde-mock hakukohdeOid9 poniosatoteutus-oid hakuOid1 :tila "julkaistu" :nimi "ponikoulun hakukohde" :muokkaaja "1.2.246.562.24.62301161440" :hakuaikaAlkaa "2000-01-01T00:00" :hakuaikaPaattyy "2000-01-02T00:00" :valintaperuste valintaperusteId2)
  (fixture/add-hakukohde-mock hakukohdeOid10 ponikoulu-oid hakuOid2 :tila "julkaistu" :nimi "ponikoulun yhteishakukohde" :muokkaaja "1.2.246.562.24.62301161440" :hakuaikaAlkaa "2000-01-01T00:00" :hakuaikaPaattyy "2020-01-01T00:00" :valintaperuste valintaperusteId2)
  (fixture/add-hakukohde-mock kk-hakukohde-oid kk-toteutus-oid kk-haku-oid :tila "julkaistu" :esitysnimi "KK-hakukohde")

  (fixture/add-oppilaitos-mock oppilaitosOid1 :tila "julkaistu" :organisaatio oppilaitosOid1
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid1 ".json")))})

  (fixture/add-oppilaitos-mock oppilaitosOid2 :tila "julkaistu" :organisaatio oppilaitosOid2
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid2 ".json")))})
  (fixture/add-oppilaitos-mock oppilaitosOid3 :tila "tallennettu" :esikatselu false :organisaatio oppilaitosOid2
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid3 ".json")))})
  (fixture/add-oppilaitos-mock oppilaitosOid4 :tila "tallennettu" :esikatselu true :organisaatio oppilaitosOid4
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid4 ".json")))})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos oppilaitosOid5 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid5 ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos oppilaitosOid6 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid6 ".json")))}})
  (fixture/add-oppilaitos-mock oppilaitosOid7 :tila "julkaistu" :organisaatio oppilaitosOid7
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" oppilaitosOid7 ".json")))})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos aakkostus-oppilaitos-oid1 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" aakkostus-oppilaitos-oid1 ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos aakkostus-oppilaitos-oid2 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" aakkostus-oppilaitos-oid2 ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos aakkostus-oppilaitos-oid3 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" aakkostus-oppilaitos-oid3 ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos aakkostus-oppilaitos-oid4 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" aakkostus-oppilaitos-oid4 ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos aakkostus-oppilaitos-oid5 {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" aakkostus-oppilaitos-oid5 ".json")))}})
  (fixture/add-oppilaitos-mock jokin-jarjestyspaikka :tila "julkaistu" :organisaatio jokin-jarjestyspaikka
                               :_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" jokin-jarjestyspaikka ".json")))})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos punkaharjun-yliopisto {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" punkaharjun-yliopisto ".json")))}})
  (fixture/add-oppilaitos-mock-without-kouta-oppilaitos helsingin-yliopisto {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" punkaharjun-yliopisto ".json")))}})

  (fixture/add-oppilaitos-mock ita-suomen-yliopisto :tila "julkaistu" :organisaatio ita-suomen-yliopisto {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" ita-suomen-yliopisto ".json")))}})
  (fixture/add-oppilaitoksen-osa-mock ita-suomen-yliopisto-avoin ita-suomen-yliopisto :tila "julkaistu" :organisaatio ita-suomen-yliopisto-avoin {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" ita-suomen-yliopisto-avoin ".json")))}})
  (fixture/add-oppilaitoksen-osa-mock ita-suomen-yliopisto-avoin-kuopio ita-suomen-yliopisto :tila "julkaistu" :organisaatio ita-suomen-yliopisto-avoin-kuopio {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" ita-suomen-yliopisto-avoin-kuopio ".json")))}})
  (fixture/add-oppilaitoksen-osa-mock ita-suomen-yliopisto-avoin-joensuu ita-suomen-yliopisto :tila "julkaistu" :organisaatio ita-suomen-yliopisto-avoin-joensuu {:_enrichedData {:organisaatio (fixture/->keywordized-json (slurp (str "test/resources/organisaatiot/" ita-suomen-yliopisto-avoin-joensuu ".json")))}})

  (fixture/add-oppilaitoksen-osa-mock oppilaitoksenOsaOid1 oppilaitosOid1 :tila "julkaistu" :organisaatio oppilaitoksenOsaOid1)
  (fixture/add-oppilaitoksen-osa-mock oppilaitoksenOsaOid2 oppilaitosOid1 :tila "arkistoitu" :organisaatio oppilaitoksenOsaOid2)
  (fixture/add-oppilaitoksen-osa-mock oppilaitoksenOsaOid3 oppilaitosOid2 :tila "julkaistu" :organisaatio oppilaitoksenOsaOid3)
  (fixture/add-oppilaitoksen-osa-mock oppilaitoksenOsaOid4 oppilaitosOid2 :tila "tallennettu" :esikatselu true :organisaatio oppilaitoksenOsaOid4)
  (fixture/add-oppilaitoksen-osa-mock oppilaitoksenOsaOid5 oppilaitosOid2 :tila "tallennettu" :esikatselu false :organisaatio oppilaitoksenOsaOid5)

  (fixture/index-oids-without-related-indices {:koulutukset [koulutusOid1 koulutusOid2 koulutusOid3 koulutusOid4 koulutusOid5 koulutusOid6 koulutusOid7 lukio-oid kk-koulutus-oid
                                                             aakkostus-koulutus-oid1 aakkostus-koulutus-oid2 aakkostus-koulutus-oid3 aakkostus-koulutus-oid3 aakkostus-koulutus-oid4 aakkostus-koulutus-oid5
                                                             keyword-koulutus-oid1 keyword-koulutus-oid2 keyword-koulutus-oid3 keyword-koulutus-oid4 keyword-koulutus-oid5 keyword-koulutus-oid6 keyword-koulutus-oid7
                                                             keyword-koulutus-oid8 keyword-koulutus-oid9 keyword-koulutus-oid10 keyword-koulutus-oid11 keyword-koulutus-oid12 keyword-koulutus-oid13
                                                             keyword-koulutus-oid14 keyword-koulutus-oid15 keyword-koulutus-oid16 keyword-koulutus-oid17 keyword-koulutus-oid18
                                                             osaamismerkki-koulutus-oid tuva-koulutus-oid puutarha-koulutus-oid]
                                               :toteutukset [toteutusOid1 toteutusOid2 toteutusOid3 toteutusOid4 toteutusOid5 toteutusOid6
                                                             lukio-toteutus-oid kk-toteutus-oid osaamismerkki-toteutus-oid tuva-toteutus-erityisopetuksena-oid
                                                             puutarha-ala-toteutus-erityisopetuksena-oid puutarha-ala-toteutus-oid]
                                               :haut [hakuOid1 hakuOid2 hakuOid3 hakuOid4 hakuOid5 kk-haku-oid]
                                               :hakukohteet [hakukohdeOid1 hakukohdeOid2 hakukohdeOid3 hakukohdeOid4 hakukohdeOid5 hakukohdeOid6 hakukohdeOid7 kk-hakukohde-oid]
                                               :valintaperusteet [valintaperusteId1 valintaperusteId2 valintaperusteId3 valintaperusteId4 valintaperusteId5 valintaperusteId6]
                                               :oppilaitokset [oppilaitosOid1 oppilaitosOid2
                                                               oppilaitosOid3
                                                               oppilaitosOid4
                                                               oppilaitosOid5 oppilaitosOid6 oppilaitosOid7
                                                               aakkostus-oppilaitos-oid1 aakkostus-oppilaitos-oid2 aakkostus-oppilaitos-oid3 aakkostus-oppilaitos-oid4 aakkostus-oppilaitos-oid5
                                                               jokin-jarjestyspaikka
                                                               ita-suomen-yliopisto]})

  ;; Punkaharjun ja Helsingin yliopistoihin kiinnitetyt koulutukset
  (fixture/index-oids-without-related-indices {:koulutukset [traktoriala-oid hevosala-oid traktoriala-oid2 hevostutkinnon-osa-oid hevososaamisala-oid yo-koulutus-oid amk-oid lukio-oid2 amm-muu-oid]
                                               :oppilaitokset [punkaharjun-yliopisto helsingin-yliopisto]})
  (export-elastic-data "konfo-backend")
  (ed-utils/stop-elasticsearch))
