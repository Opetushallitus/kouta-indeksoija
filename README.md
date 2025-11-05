# kouta-indeksoija

## 1. Palvelun tehtävä

Indeksoi Elasticsearchiin koulutustarjonnan dataa, jotta sitä voidaan jakaa tehokkaasti eri palveluille, erityisesti 
konfo-ui:lle (beta.opintopolku.fi) ja sen koulutushaulle. 

## 2. Arkkitehtuuri

Kouta-indeksoija on clojurella toteutettu ring/compojure/compojure-api web API. Indeksoija kuuntelee kouta-backendin
lähettämiä sqs-viestejä ja viestin saatuaan hakee viestissä olleella id:llä entiteetin tiedot kouta-backendin 
rajapinnasta ja indeksoi tiedot Elasticsearchiin. Pyörittää myös cron jobia joka tarkastaa kouta-backendin muuttuneet
tiedot siinä tapauksessa että jokin tieto on jäänyt indeksoimatta.

Kouta-indeksoijaan integroituvia oph:n palveluja on mm. konfo-backend, kouta-internal, kouta-external. 

Indeksoijan käyttämät tietolähteet:

| Tietolähde    | Haettavat tiedot                                                                                                            |
|---------------|-----------------------------------------------------------------------------------------------------------------------------|
| eperusteet    | Ammatillisten koulutusten eperusteet sekä osaamisalojen ja tutkinnon osien kuvaukset                                        |
| koodisto      | Konfo-ui:ssa tarvittavien koodistojen koodien lokalisaatiot. Lisäksi tarvittavat koodirelaatiot ja niidenkin lokalisaatiot. |
| kouta-backend | Koulutukset, koulutusten toteutukset, hakukohteet, haut, valintaperustekuvaukset ja SORA-kuvaukset                          |
| lokalisaatio  | Konfo-ui:ssa käyttäjille näytettävät tekstit kolmella eri kielellä (suomi, ruotsi ja englanti)                              |
| organisaatio  | Koulutustoimijoiden, oppilaitosten ja toimipisteiden perustiedot ja niiden organisaatiohierarkia                            |

## 3. Kehitysympäristö

### 3.1. Esivaatimukset

Asenna haluamallasi tavalla koneellesi
1. [Leiningen](https://leiningen.org/) (Valitse asennus haluammallasi package managerilla)
2. [Docker](https://www.docker.com/get-started) (Elasticsearchia ja localstackia varten)
3. [AWS cli](https://aws.amazon.com/cli/) (SQS-jonoja varten)
4. [IntelliJ IDEA](https://www.jetbrains.com/idea/) + [Cursive plugin](https://cursive-ide.com/) tai haluamasi kehitystökalu

Lisäksi tarvitset Java SDK:n (Unix pohjaisissa käyttöjärjestelmissä auttaa esim. [SDKMAN!](https://sdkman.io/)). 
Katso [.travis.yml](.travis.yml) mitä versioita sovellus käyttää. Kirjoitushetkellä käytössä openJDK11.

Jos käytät IDEA:aa ja koodissa näkyy paljon virheitä, right clickaa project.clj-tiedostoa ja 
valitse 'Add as Leiningen Project' 

Indeksoijan saa konfiguroitua luomalla tiedoston `dev_resources/config.edn` (laitettu .gitignoreen) ja asettamalla sinne
tarvittavat arvot. Tiedostosta `dev_resources/config.edn.template` näkee mitä arvoja sovellus tarvitsee toimiakseen.
Kirjoitushetken esimerkki konfigista, joka toimii untuva-testiympäristöä vasten lokaalilla Elasticsearchilla:

```clojure
{:elastic-url "http://localhost:9200"
 :cas {:username "kouta"
 :password "tähän cas:n salasana"}
 :hosts {:kouta-backend "https://virkailija.untuvaopintopolku.fi"
 :kouta-external "https://virkailija.untuvaopintopolku.fi"
 :virkailija-internal "https://virkailija.untuvaopintopolku.fi"
 :cas "https://virkailija.untuvaopintopolku.fi"
 :ataru-hakija ""}
 :queue {:priority {:name "koutaIndeksoijaPriority" :health-threshold 10}
 :fast {:name "koutaIndeksoijaFast" :health-threshold 10}
 :slow {:name "koutaIndeksoijaSlow" :health-threshold 10}
 :dlq {:name "koutaIndeksoijaDlq" :health-threshold 10}
 :notifications {:name "koutaIndeksoijaNotifications" :health-threshold 10}
 :notifications-dlq {:name "koutaIndeksoijaNotificationsDlq" :health-threshold 10}}
 :sqs-region ""
 :sqs-endpoint "http://localhost:4566"
 :lokalisaatio-indexing-cron-string "* 0/30 * ? * *"
 :organisaatio-indexing-cron-string "* 0 0 ? * *"
 :queueing-cron-string "*/15 * * ? * *"
 :notifier-targets ""
 :kouta-indeksoija-kouta-cache-time-seconds 3600
 :kouta-indeksoija-kouta-cache-size 1000
 :kouta-indeksoija-organisaatio-cache-time-millis 1800000}
```

Testiympäristöä voi vaihtaa laittamalla yllä olevasta configista untuva sanojen paikalle toisen testiympäristön nimen.
Cas-salasanan saa kaivettua untuvan ympäristökohtaisesta reposta opintopolku.yml tiedostosta kohdasta `kouta_indeksoija_cas_password`

Jos et tiedä mitä tämä tarkoittaa, kysy neuvoa kehitystiimiltä tai OPH:n ylläpidolta.

### 3.2. Testien ajaminen

Testit ajetaan [Kaocha test runnerilla](https://cljdoc.org/d/lambdaisland/kaocha/1.87.1366/doc/readme).
Kaikki testit saa ajettua komentoriviltä komennolla `lein test`.

Yksittäisen testitiedoston saa ajettua lisäämällä `^:focus`-tagin testitiedoston namespace-määrittelyn alkuun, esim.
`ns ^:focus kouta-indeksoija-service.indexer.kouta-indexer-test`.

Yksittäisen testin saa ajettua lisäämällä `^:focus`-tagi ennen ajettavan testin nimeä, esim.
`deftest ^:focus index-oppilaitos-test`.

Testejä voi ajaa myös watch-modessa komennolla `lein test --watch` eli test runner ajaa testit automaattisesti uudestaan, kun koodia tai testejä muutetaan.

Testit käynnistävät Elasticsearchin docker-kontissa satunnaiseen vapaaseen porttiin.
Watch-moodissa elasticsearch käynnistetään vain kerran, kun testiajo käynnistetään, ja se pysyy taustalla käynnissä kunnes testiajo lopetetaan.
(Poikkeuksena muutamat mm. indeksien luomiseen liittyvät testit, jotka vaativat elasticin uudelleenkäynnistyksen testin alussa.)

### 3.3. Ajaminen lokaalisti

Ennen indeksoijan ajamista lokaalisti täytyy pyörimässä olla
1. Elasticsearch
2. localstackin sqs-jonot

---
#### Elasticsearch-kontin käynnistys

Elasticsearchia voi pyörittää docker-kontissa siten että data tallennetaan levylle vaikka kontin 
sammuttaisi. Tämä onnnistuu ajamalla ensin (ainoastataan ensimmäisellä kerralla):

```shell
docker volume create kouta-elastic-data
```

elasticsearch-koutan lataaminen vaatii kirjautumista ecr:n

```shell
aws ecr get-login-password --region eu-west-1 --profile oph-utility | docker login --username AWS --password-stdin 190073735177.dkr.ecr.eu-west-1.amazonaws.com
```

Jonka jälkeen kontin saa käyntiin komennolla:
```shell
docker run --rm --name kouta-elastic --env "discovery.type=single-node" -p 127.0.0.1:9200:9200 -p 127.0.0.1:9300:9300 -e xpack.security.enabled=false -v kouta-elastic-data:/usr/share/elasticsearch/data 190073735177.dkr.ecr.eu-west-1.amazonaws.com/utility/elasticsearch-kouta:8.15.0
```
Jos tulee tarve poistaa data, komennolla `docker volume --help` saa apua volumeiden hallinnointiin.

Ilman volumea ajaminen onnistuu komennolla:
```shell
docker run --rm --name kouta-elastic --env "discovery.type=single-node" -p 127.0.0.1:9200:9200 -p 127.0.0.1:9300:9300 -e xpack.security.enabled=false 190073735177.dkr.ecr.eu-west-1.amazonaws.com/utility/elasticsearch-kouta:8.15.0
```
Tässä tapauksessa tiedot häviävät kun kontin sammuttaa.

#### Elasticsearch-kontin buildaus

HUOM! Tämä tarvitsee tehdä vain jos konttiin tehdään muutoksia

```
elastic/build.sh
elastic/deploy.sh
```

---
#### Localstack SQS-jonot
Indeksoija vaatii lokaalin SQS-palvelun porttiin 4567. Sen voi käynnistää docker-konttiin ajamalla skriptin 
`tools/start_localstack` ja pysäyttää skriptillä `tools/stop_localstack`. 

`tools/send_local` skriptiä voi käyttää viestien lähetykseen lokaaleihin jonoihin, jos jostain syystä
sellainen tarve tulee.

---

Kun Elasticsearch ja sqs-jonot ovat pyörimässä indeksoijan saa käyntiin komennolla `AWS_ACCESS_KEY_ID=randomKeyIdForLocalstack AWS_SECRET_ACCESS_KEY=randomKeyForLocalstack lein dev`

Tämä avaa swaggerin selaimeen osoitteeseen `http://localhost:8100/kouta-indeksoija/swagger/index.html`

### 3.4. Kehitystyökalut

Suositeltava kehitysympäristö on [IntelliJ IDEA](https://www.jetbrains.com/idea/) + [Cursive plugin](https://cursive-ide.com/)
mutta sovelluksen ja testien ajamisen kannalta nämä eivät ole välttämättömiä. Jos et tee paljon clojure-kehitystä, myöskään cursive ei
ole välttämätön.

### 3.5. Testidata

Testidataa saa omaan lokaaliin Elasticsearchiin laittamalla tiedoston `dev_resources/config.edn` arvot

```clojure
:hosts {:kouta-backend "https://virkailija.untuvaopintopolku.fi"
:kouta-external "https://virkailija.untuvaopintopolku.fi"
:virkailija-internal "https://virkailija.untuvaopintopolku.fi"
:cas "https://virkailija.untuvaopintopolku.fi"
```
osoittamaan haluamaasi testiympäristöä vasten ja ajamalla swaggerista `POST /kouta-indeksoija/api/kouta/all`.
Voit myös indeksoida vain yksittäisen entiteetin, esimerkiksi ajamalla swaggerista 
`POST /kouta-indeksoija/api/kouta/koulutus/:oid` 

### 3.6 Mock-datan generointi

Muiden palvelujen (kouta-internal, kouta-external, konfo-backend) testien käyttämän Elasticsearch mock-datadumpin generointi tapahtuu skripteillä, jotka löytyvät hakemistosta test/mocks.

palvelunnimi_mocks.clj -tiedostoissa on skripti kyseiselle palvelulle. Kun olet tehnyt tarvittavat muutokset 
testidatan generoivaan skriptiin, aja komento:

```
lein elasticdump:<palvelun nimi>
```

eli esimerkiksi:

```
lein elasticdump:konfo-backend
```

Se suorittaa skriptin ja luo elasticsearch-testidata-dumpin hakemistoon elasticdump/palvelunnimi

Korvaa sitten kyseisen palvelun repositoriossa elastic_dump -hakemiston sisältö tiedostot tuohon hakemistoon luoduilla tiedostoilla.

## 4. Ympäristöt

### 4.1. Testiympäristöt

Testiympäristöjen swaggerit löytyvät seuraavista osoitteista:

- [untuva](https://virkailija.untuvaopintopolku.fi/kouta-indeksoija/swagger)
- [hahtuva](https://virkailija.hahtuvaopintopolku.fi/kouta-indeksoija/swagger)
- [QA eli pallero](https://virkailija.testiopintopolku.fi/kouta-indeksoija/swagger)

Jotta pääset näihin käsiksi täytyy sinulla olla OPH:n ympäristöihin päästävä VPN:n päällä ja indeksoijan
tunnus ja salasana tiedossa. Näihin apua saa kehitystiimiltä tai OPH:n ylläpidolta.

### 4.2. Asennus

Asennus hoituu samoilla työkaluilla kuin muidenkin OPH:n palvelujen. 
[Cloud-basen dokumentaatiosta](https://github.com/Opetushallitus/cloud-base/tree/master/docs) ja ylläpidolta löytyy apuja.

Asennuksen yhteydessä tapahtuvaan mahdolliseen uudelleenindeksointiin löytyy ohjeet 
täältä: [Katkoton indeksointi](README_INDEKSOINTI.md)

Jos tekemäsi muutos koodiin muuttaa indeksoijan tallentaman, palvelusta ulos välitettävän datan muotoa, päivitä 
project.clj tiedostoon kouta-indeksoijan versionumeroa semanttisen versioinnin mukaisesti. 
Eli jos teet taaksepäinyhteensopivan muutoksen, esimerkiksi lisäät indeksiin uuden kentän, nosta minor versiota 
(6.2.0 -> 6.3.0). Jos taas teet muutoksen joka ei ole taaksepäinyhteensopiva, esimerkiksi poistat indeksistä kentän, 
nosta major versiota (6.2.0 -> 7.0.0).

### 4.3. Buildaus kehityshaarasta

Mikäli haluaa uuden version paketin vain lokaalia kehitystä varten, saa sen luotua komennolla `lein install`, joka luo paketin
lokaaliin Maven repoon.

### 4.3. Lokit

Indeksoijan lokit löytyvät AWS:n cloudwatchista log groupista <testiympäristön nimi>-app-kouta-indeksoija (esim. hahtuva-app-kouta-indeksoija). Lisäohjeita näihin ylläpidolta.

## 5. Troubleshooting

Jos törmäät seuraavaan virheeseen esimerkiksi testejä ajaessasi:

`An error occurred (AccessDeniedException) when calling the GetAuthorizationToken operation: User: arn:aws:iam::123456789:user/it-ankka@madness.com is not authorized to perform: ecr:GetAuthorizationToken on resource: * with an explicit deny in an identity-based policy`

Aja seuraava loitsu (mahdollisesti joutuu myös ajamaan `aws configure` ennen tätä):

AWS CLI v1:

`aws ecr get-login --no-include-email --region eu-west-1 --profile oph-utility`

AWS CLI v2:

`aws ecr get-login-password --region eu-west-1 --profile oph-utility | docker login --username AWS --password-stdin 190073735177.dkr.ecr.eu-west-1.amazonaws.com`

ARM-arkkitehtuurilla elasticsearchin docker kontti buildattava itse.

`elastic/create-bundles.sh`
`elastic/build.sh`

## 6. Lisätietoa

Vanhan readme:n tiedot joiden paikkansapitävyyttä ei ole selvitetty:

---
---
---
#### Repl for development

During development it's easy to test what you have done by using a REPL. This works best
with IDEA and cursive (others also, but the instructions here apply for Cursive).

Create a REPL run configuration. When you import the project one should be created for you.
If this is not the case, right click project.clj and select run. Then edit the configuration:

* Name it something like Dev REPL
* Add a JVM argument `-Dtest=false`
* When you start the repl type `(go)` to activate mount.core (brings settings to namespaces).

#### Repl for autotesting

Create another repl configuration, e.g. Test repl and add JVM argument `-Dtest=true`.

To run tests every time code is changed:
* `(use 'midje.repl)` (no need to use `(go)` here)
* `(autotest)`

### Running the application locally

#### Requirements

##### Notifier
The application can notify others when information is indexed. This is controlled with `:notifier-targets`
value in `dev_resources/config.edn`. It should be defaulted to `""`, ie. no changes will be sent.

When wanting to validate locally that the notifications are working, one of the easiest way is with `dummy-web-server.py` in `tools`:
* Run `python2 tools/dummy-web-server.py 9900` to start it in port 9900.
* Change `:notifier-targets` in `dev_resources/config.edn` to `"http://localhost:9900"`
* You can edit the script to set the return code and headers.

The script will log every access to console.


### Nuking ElasticSearch settings

If you need to nuke ElasticSearch analyze settings, there are two tools that can be used test different settings and see how
they affect queries and word analysis.

`test/misc/analyze_settings_test_tool.clj` can be used if you want to test how different analyze settings affect queries.
It mimics both nested structure in kouta-indeksoija search indexes and search queries generated by konfo-backend for koulutus
and oppilaitos searches.

`test/misc/analyzers_test_tool.clj` can be used to test how different search analyze settings handle different words and phraises.

Both tools have main method and they can be run for example in Idea by choosing run command when clicking right mouse button.
Make sure you have test profile enabled in `Leiningen Projects/Profiles`.

### Configuration

App config is handled with Mount, cprops and in (mostly dev) cases with environment variables. To add a conf parameter
to the application, add it to dev_resources/config.edn (for development) AND oph-configuration/config.edn.template. In
order for the template file to work in non-local environments, the variable must also be added to the variable file in
git@git.oph.ware.fi:environment-{ophitest|ophp|ophprod|vagrant}.git in deploy/<env>_vars.yml.

NOTE: The cron-string variable roughly follows cron scheduler syntax with a few alterations shown
[here](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html).

---
---
---

## License

Copyright (c) 2017 The Finnish National Board of Education - Opetushallitus

For details see LICENSE.txt
