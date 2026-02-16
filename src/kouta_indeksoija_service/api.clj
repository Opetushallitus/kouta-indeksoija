(ns kouta-indeksoija-service.api
  (:require [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.elastic.tools :refer [init-elastic-client]]
            [kouta-indeksoija-service.indexer.indexer :as indexer]
            [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
            [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
            [kouta-indeksoija-service.indexer.kouta.haku :as haku]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
            [kouta-indeksoija-service.indexer.kouta.valintaperuste :as valintaperuste]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]
            [kouta-indeksoija-service.indexer.kouta.sorakuvaus :as sorakuvaus]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :as eperuste]
            [kouta-indeksoija-service.indexer.eperuste.osaamismerkki :as osaamismerkki]
            [kouta-indeksoija-service.indexer.eperuste.osaamisalakuvaus :as osaamisalakuvaus]
            [kouta-indeksoija-service.indexer.lokalisointi.lokalisointi :as lokalisointi]
            [kouta-indeksoija-service.lokalisointi.service :as lokalisointi-service]
            [clj-log.access-log :refer [with-access-logging]]
            [compojure.api.sweet :refer :all]
            [compojure.route :as route]
            [ring.util.http-response :refer :all]
            [mount.core :as mount]
            [clojure.tools.logging :as log]
            [environ.core]
            [kouta-indeksoija-service.scheduled.jobs :as jobs]
            [kouta-indeksoija-service.queuer.queuer :as queuer]
            [kouta-indeksoija-service.notifier.notifier :as notifier]
            [kouta-indeksoija-service.util.tools :refer [comma-separated-string->vec]]
            [kouta-indeksoija-service.queue.admin :as sqs]
            [schema.core :as schema]))

(schema/defschema Indices [schema/Str])

(defn init []
  (mount/start)
  (log/info "Running init")
  (intern 'clj-log.access-log 'service "kouta-indeksoija")
  (init-elastic-client)
  (if (and (admin/initialize-cluster-settings)
           (admin/check-elastic-status)
           (admin/initialize-indices))
    (do
      (log/info "Running schedule jobs")
      (jobs/schedule-jobs)
      (log/info "Done schedule jobs"))
    (do
      (log/error "Application startup canceled due to Elastic client error or absence.")
      (System/exit 0)))
  (log/info "Done init"))

(defn stop []
  (jobs/reset-jobs)
  (mount/stop))

(defn error-handler*
  [^Exception e data request]
  (log/error e))

(defn- with-notifications
  [keyword notify result]
  (if notify (notifier/notify {keyword result}))
  (ok {:result result}))

(def app
  (api
   {:swagger {:ui "/kouta-indeksoija/swagger"
              :spec "/kouta-indeksoija/swagger.json"
              :data {:info {:title "kouta-indeksoija"
                            :description "Elasticsearch wrapper for tarjonta api."}}}
    :exceptions {:handlers {:compojure.api.exception/default error-handler*}}}
   (context "/kouta-indeksoija/api" []

     (GET "/healthcheck" [:as request]
       :summary "Healthcheck API."
       (with-access-logging request (ok "OK")))

     (GET "/healthcheck/deep" [:as request]
       :summary "Palauttaa 500, jos sqs-jonot tai elasticsearch ei ole terveitä"
       (with-access-logging request (let [[sqs-healthy? sqs-body] (sqs/healthcheck)
                                          [els-healthy? els-body] (admin/healthcheck)
                                          body {:sqs-health sqs-body :elasticsearch-health els-body}]
                                      (if (and sqs-healthy? els-healthy?)
                                        (ok body)
                                        (internal-server-error body)))))

     (context "/rebuild" []
       :tags ["rebuild"]

       (GET "/indices/list" [:as request]
         :summary "Listaa kaikki indeksit ja niihin liitetyt aliakset"
         (with-access-logging request (ok (admin/list-indices-and-aliases))))

       (GET "/indices/list/virkailija" [:as request]
         :summary "Listaa kaikki virkailija-puolen käyttämät indeksit"
         (with-access-logging request (ok (admin/list-virkailija-indices))))

       (GET "/indices/list/oppija" [:as request]
         :summary "Listaa kaikki oppija-puolen käyttämät indeksit"
         (with-access-logging request (ok (admin/list-oppija-indices))))

       (GET "/indices/list/unused" [:as request]
         :summary "Listaa kaikki indeksit, joita ei enää käytetä (niissä ei ole aliaksia)"
         (with-access-logging request (ok (admin/list-unused-indices))))

       (POST "/indices/all" [:as request]
         :summary "Luo uudelleen kaikki indeksit katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-all-indices-for-reindexing))))

       (POST "/indices/one" [:as request]
         :summary "Luo uudelleen yhden indeksin katkotonta uudelleenindeksointia varten."
         :body [index-name String]
         (with-access-logging request (try (ok (admin/initialize-new-index-for-reindexing index-name))
                                           (catch IllegalArgumentException e (bad-request (ex-message e))))))

       (POST "/indices/kouta" [:as request]
         :summary "Luo uudelleen kaikki kouta-datan (ja oppilaitosten!) indeksit katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-kouta-indices-for-reindexing))))

       (POST "/indices/eperuste" [:as request]
         :summary "Luo uudelleen kaikki eperuste-datan indeksit katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-eperuste-indices-for-reindexing))))

       (POST "/indices/osaamismerkki" [:as request]
         :summary "Luo uudelleen osaamismerkki-indeksin katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-osaamismerkki-indices-for-reindexing))))

       (POST "/indices/koodisto" [:as request]
         :summary "Luo uudelleen kaikki koodisto-datan indeksit katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-koodisto-indices-for-reindexing))))

       (POST "/indices/lokalisointi" [:as request]
         :summary "Luo uudelleen kaikki lokalisointi-datan indeksit katkotonta uudelleenindeksointia varten."
         (with-access-logging request (ok (admin/initialize-lokalisointi-indices-for-reindexing))))

       (DELETE "/indices/unused" [:as request]
         :summary "Poistaa kaikki indeksit, joita ei enää käytetä (niissä ei ole aliaksia)"
         (with-access-logging request (ok (admin/delete-unused-indices))))

       (DELETE "/indices" [:as request]
         :summary "Poistaa listatut indeksit."
         :body [indices Indices]
         (with-access-logging request (ok (admin/delete-indices indices))))

       (POST "/indices/aliases/sync" [:as request]
         :summary "Synkkaa oppijan aliakset osoittamaan samoihin indekseihin kuin virkailijan aliakset"
         (with-access-logging request (ok (admin/sync-all-aliases)))))

     (context "/kouta" []
       :tags ["kouta"]

       (POST "/all" [:as request]
         :query-params [{since :- Long 0}]
         :summary "Indeksoi uudet ja muuttuneet koulutukset, toteutukset, hakukohteet, haut ja valintaperusteet kouta-backendistä. Default kaikki."
         (with-access-logging request (ok {:result (if (= 0 since)
                                                     (indexer/index-all-kouta)
                                                     (indexer/index-since-kouta since))})))

       (POST "/koulutus" [:as request]
         :summary "Indeksoi koulutuksen tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :koulutukset notify (indexer/index-koulutus oid))))

       (POST "/koulutus/quick" [:as request]
         :summary "Indeksoi kevyesti vain koulutuksen tiedot kouta-backendistä, jotta saadaan kouta-ui:n
         listausnäkymät päivittymään nopeammin. Ei indeksoi muita koulutukseen liittyviä entiteettejä!"
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :koulutukset notify (indexer/quick-index-koulutus oid))))

       (POST "/koulutukset" [:as request]
         :summary "Indeksoi kaikki koulutukset kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :koulutukset notify (indexer/index-all-koulutukset))))

       (POST "/toteutus" [:as request]
         :summary "Indeksoi toteutuksen tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :toteutukset notify (indexer/index-toteutus oid))))

       (POST "/toteutus/quick" [:as request]
         :summary "Indeksoi kevyesti vain toteutuksen tiedot kouta-backendistä, jotta saadaan kouta-ui:n
         listausnäkymät päivittymään nopeammin. Ei indeksoi muita toteutukseen liittyviä entiteettejä!"
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :toteutukset notify (indexer/quick-index-toteutus oid))))

       (POST "/toteutukset" [:as request]
         :summary "Indeksoi kaikki toteutukset kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :toteutukset notify (indexer/index-all-toteutukset))))

       (POST "/hakukohde" [:as request]
         :summary "Indeksoi hakukohteen tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :hakukohteet notify (indexer/index-hakukohde oid))))

       (POST "/hakukohde/quick" [:as request]
         :summary "Indeksoi kevyesti vain hakukohteen tiedot kouta-backendistä, jotta saadaan kouta-ui:n
         listausnäkymät päivittymään nopeammin. Ei indeksoi muita hakukohteeseen liittyviä entiteettejä!"
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :hakukohteet notify (indexer/quick-index-hakukohde oid))))

       (POST "/hakukohteet" [:as request]
         :summary "Indeksoi kaikki hakukohteet kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :hakukohteet notify (indexer/index-all-hakukohteet))))

       (POST "/haku" [:as request]
         :summary "Indeksoi haun tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :haku notify (indexer/index-haku oid))))

       (POST "/haku/quick" [:as request]
         :summary "Indeksoi kevyesti vain haun tiedot kouta-backendistä, jotta saadaan kouta-ui:n
         listausnäkymät päivittymään nopeammin. Ei indeksoi muita hakuun liittyviä entiteettejä!"
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :haku notify (indexer/quick-index-haku oid))))

       (POST "/haut" [:as request]
         :summary "Indeksoi kaikki haut kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :haku notify (indexer/index-all-haut))))

       (POST "/valintaperuste" [:as request]
         :summary "Indeksoi valintaperusteen tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :valintaperusteet notify (indexer/index-valintaperuste oid))))

       (POST "/valintaperuste/quick" [:as request]
         :summary "Indeksoi kevyesti vain valintaperusteen tiedot kouta-backendistä, jotta saadaan kouta-ui:n
         listausnäkymät päivittymään nopeammin. Ei indeksoi muita valintaperusteeseen liittyviä entiteettejä!"
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :valintaperusteet notify (indexer/quick-index-valintaperuste oid))))

       (POST "/valintaperusteet" [:as request]
         :summary "Indeksoi kaikki valintaperusteet kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :valintaperusteet notify (indexer/index-all-valintaperusteet))))

       (POST "/sorakuvaus" [:as request]
         :summary "Indeksoi sorakuvausten tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :sorakuvaukset notify (indexer/index-sorakuvaus oid))))

       (POST "/sorakuvaus/quick" [:as request]
         :summary "Indeksoi sorakuvausten tiedot kouta-backendistä."
         :query-params [oid :- String
                        {notify :- Boolean false}]
         (with-access-logging request (with-notifications :sorakuvaukset notify (indexer/quick-index-sorakuvaus oid))))

       (POST "/sorakuvaukset" [:as request]
         :summary "Indeksoi kaikki sorakuvaukset kouta-backendistä."
         :query-params [{notify :- Boolean false}]
         (with-access-logging request (with-notifications :sorakuvaukset notify (indexer/index-all-sorakuvaukset)))))

     (context "/indexed" []
       :tags ["indexed"]

       (GET "/koulutus" [:as request]
         :summary "Hakee yhden koulutuksen oidin perusteella. (HUOM! Täällä on koulutuksia, jotka eivät näy oppijan koulutushaussa)"
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (koulutus/get-from-index oid)})))

       (GET "/toteutus" [:as request]
         :summary "Hakee yhden toteutukset oidin perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (toteutus/get-from-index oid)})))

       (GET "/hakukohde" [:as request]
         :summary "Hakee yhden hakukohteen oidin perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (hakukohde/get-from-index oid)})))

       (GET "/haku" [:as request]
         :summary "Hakee yhden haun oidin perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (haku/get-from-index oid)})))

       (GET "/valintaperuste" [:as request]
         :summary "Hakee yhden valintaperusteen id:n perusteella."
         :query-params [id :- String]
         (with-access-logging request (ok {:result (valintaperuste/get-from-index id)})))

       (GET "/koulutus-haku" [:as request]
         :summary "Hakee yhden koulutuksen tiedot koulutusten hakuindeksistä (oppijan koulutushaku) oidin perusteella. Vain julkaistuja koulutuksia."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (koulutus-search/get-from-index oid)})))

       (GET "/oppilaitos" [:as request]
         :summary "Hakee yhden oppilaitoksen oidin perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (oppilaitos/get-from-index oid)})))

       (GET "/oppilaitos-haku" [:as request]
         :summary "Hakee yhden oppilaitoksen tiedot oppilaitosten hakuindeksistä (oppijan oppilaitoshaku) oidin perusteella. Vain julkaistuja oppilaitoksia."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (oppilaitos-search/get-from-index oid)})))

       (GET "/sorakuvaus" [:as request]
         :summary "Hakee yhden sorakuvauksen oidin perusteella."
         :query-params [id :- String]
         (with-access-logging request (ok {:result (sorakuvaus/get-from-index id)})))

       (GET "/eperuste" [:as request]
         :summary "Hakee yhden ePerusteen oidin (idn) perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (eperuste/get-from-index oid)})))

       (GET "/osaamismerkki" [:as request]
         :summary "Hakee yhden osaamismerkin tiedot koodiUrin perusteella."
         :query-params [koodiUri :- String]
         (with-access-logging request (ok {:result (osaamismerkki/get-from-index koodiUri)})))

       (GET "/osaamisalakuvaus" [:as request]
         :summary "Hakee yhden osaamisalakuvaus oidin (idn) perusteella."
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (osaamisalakuvaus/get-from-index oid)})))

       (GET "/lokalisointi" [:as request]
         :summary "Hakee lokalisoinnit annetulla kielellä."
         :query-params [lng :- String]
         (with-access-logging request (ok {:result (lokalisointi/get-from-index lng)}))))

     (context "/admin" []
       :tags ["admin"]

       (GET "/index/status" [:as request]
         :summary "Hakee klusterin ja indeksien tiedot."
         (with-access-logging request (ok {:result (admin/get-elastic-status)})))

       (GET "/aliases/all" [:as request]
         :summary "Listaa kaikki indeksit ja niihin liitetyt aliakset."
         (with-access-logging request (ok (admin/list-indices-and-aliases))))

       (GET "/alias" [:as request]
         :summary "Listaa aliakseen liitetyt indeksit"
         :query-params [alias :- String]
         (with-access-logging request (ok (admin/list-indices-with-alias alias))))

       (POST "/aliases/sync" [:as request]
         :summary "Synkkaa kaikki virkailijan ja oppijan aliakset osoittamaan samoihin indekseihin"
         (with-access-logging request (ok (admin/sync-all-aliases))))

       (GET "/queue/status" [:as request]
         :summary "Palauttaa tiedon kaikkien sqs-jonojen tilasta"
         (with-access-logging request (ok (sqs/status))))

       (GET "/index/query" [:as request]
         :summary "Tekee haun haluttuun indeksiin"
         :query-params [index :- String
                        query :- String]
         (with-access-logging request (ok (admin/search index query))))

       (POST "/index/delete" [:as request]
         :summary "Poistaa halutun indeksin. HUOM! ÄLÄ KÄYTÄ, ELLET OLE VARMA, MITÄ TEET!"
         :query-params [index :- String]
         (with-access-logging request (ok (admin/delete-index index))))

       (POST "/lokalisointi/key-value-pairs" [:as request]
         :summary "Konvertoi jsonin avain-arvo-pareiksi käännösten tekemistä varten"
         :body [body (describe schema/Any "JSON-muotoiset käännökset (translation.json)")]
         (with-access-logging request (ok (lokalisointi-service/->translation-keys body))))

       (POST "/lokalisointi/json" [:as request]
         :summary "Konvertoi avain-arvo-parit jsoniksi"
         :body [body (describe schema/Any "Avain-arvo-muotoiset käännökset")]
         (with-access-logging request (ok (lokalisointi-service/->json body))))

       (POST "/lokalisointi/json/konfo/save" [:as request]
         :summary "Tallentaa konfo-ui:n käännöstiedoston (translation.json) lokalisointi-palveluun. Ei ylikirjoita olemassaolevia käännösavaimia."
         :query-params [lng :- String]
         :body [body (describe schema/Any "JSON-muotoiset käännökset")]
         (with-access-logging request (ok (lokalisointi-service/save-translation-json-to-localisation-service "konfo" lng body))))

       (POST "/lokalisointi/json/kouta/save" [:as request]
         :summary "Tallentaa kouta-ui:n käännöstiedoston json-muodossa lokalisointi-palveluun. Ei ylikirjoita olemassaolevia käännösavaimia."
         :query-params [lng :- String]
         :body [body (describe schema/Any "JSON-muotoiset käännökset")]
         (with-access-logging request (ok (lokalisointi-service/save-translation-json-to-localisation-service "kouta" lng body)))))

     (context "/jobs" []
       :tags ["jobs"]

       (GET "/list" [:as request]
         :summary "Listaa tiedot järjestelmän ajastetuista prosesseista"
         (with-access-logging request (ok (jobs/get-jobs-info))))

       (POST "/pause-sqs" [:as request]
         :summary "Keskeyttää prosessin, joka lukee muutoksia sqs-jonosta indeksoitavaksi. HUOM!! Jos prosessin keskeyttää, kouta-tarjonnan muutokset eivät välity oppijan puolelle ja esikatseluun!"
         (with-access-logging request (ok (jobs/pause-sqs-job))))

       (POST "/resume-sqs" [:as request]
         :summary "Käynnistää prosessin, joka lukee muutoksia sqs-jonosta indeksoitavaksi"
         (with-access-logging request (ok (jobs/resume-sqs-job))))

       (POST "/pause-notification-sqs" [:as request]
         :summary "Keskeyttää prosessin, joka lukee notifikaatioita sqs-jonosta ja lähettää ne ulkoisille integraatioille. HUOM!! Jos prosessin keskeyttää, kouta-tarjonnan muutokset eivät välity ulkoisille integraatiolle."
         (with-access-logging request (ok (jobs/pause-notification-job))))

       (POST "/resume-notification-sqs" [:as request]
         :summary "Käynnistää prosessin, joka lukee notifikaatioita sqs-jonosta ja lähettää ne ulkoisille integraatioille."
         (with-access-logging request (ok (jobs/resume-notification-job))))

       (POST "/pause-queueing" [:as request]
         :summary "Keskeyttää prosessin, joka siirtää mm. ePerusteiden ja organisaatioden muutokset sqs-jonoon odottamaan indeksointia"
         (with-access-logging request (ok (jobs/pause-queueing-job))))

       (POST "/resume-queueing" [:as request]
         :summary "Käynnistää prosessin, joka siirtää mm. ePerusteiden ja organisaatioden muutokset sqs-jonoon odottamaan indeksointia"
         (with-access-logging request (ok (jobs/resume-queueing-job))))

       (POST "/pause-lokalisointi-indexing" [:as request]
         :summary "Keskeyttää prosessin, joka indeksoi lokalisointeja lokalisaatiopalvelusta"
         (with-access-logging request (ok (jobs/pause-lokalisaatio-indexing-job))))

       (POST "/resume-lokalisointi-indexing" [:as request]
         :summary "Käynnistää prosessin, joka indeksoi lokalisointeja lokalisaatiopalvelusta"
         (with-access-logging request (ok (jobs/resume-lokalisaatio-indexing-job))))

       (POST "/pause-organisaatio-indexing" [:as request]
         :summary "Keskeyttää prosessin, joka indeksoi organisaatiot organisaatiopalvelusta"
         (with-access-logging request (ok (jobs/pause-organisaatio-indexing-job))))

       (POST "/resume-organisaatio-indexing" [:as request]
         :summary "Käynnistää prosessin, joka indeksoi organisaatiot organisaatiopalvelusta"
         (with-access-logging request (ok (jobs/resume-organisaatio-indexing-job)))))

     (context "/indexer" []
       :tags ["indexer"]

       (POST "/eperuste" [:as request]
         :summary "Indeksoi ePerusteen ja sen osaamisalat (oid==id)"
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (indexer/index-eperuste oid)})))

       (POST "/osaamismerkki" [:as request]
         :summary "Indeksoi osaamismerkin"
         :query-params [koodiUri :- String]
         (with-access-logging request (ok {:result (indexer/index-osaamismerkki koodiUri)})))

       (POST "/organisaatio" [:as request]
         :summary "Indeksoi oppilaitoksen"
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (indexer/index-oppilaitos oid)})))

       (POST "/koodistot" [:as request]
         :summary "Indeksoi (filtereissä käytettävien) koodistojen uusimmat versiot."
         :query-params [{koodistot :- String "maakunta,kunta,oppilaitoksenopetuskieli,kansallinenkoulutusluokitus2016koulutusalataso1,kansallinenkoulutusluokitus2016koulutusalataso2,koulutustyyppi,opetuspaikkakk,hakutapa,valintatapajono,pohjakoulutusvaatimuskonfo,lukiopainotukset,lukiolinjaterityinenkoulutustehtava,osaamisala,kielivalikoima,opetusaikakk,sosiaalinenmedia,painotettavatoppiaineetlukiossa"}]
         (with-access-logging request (ok {:result (indexer/index-koodistot (comma-separated-string->vec koodistot))})))

       (POST "/lokalisointi" [:as request]
         :summary "Indeksoi oppijan puolen lokalisoinnit lokalisaatiopalvelusta annetulla kielellä (fi/sv/en)"
         :query-params [lng :- String]
         (with-access-logging request (ok (indexer/index-lokalisointi lng))))

       (POST "/lokalisointi/kaikki" [:as request]
         :summary "Indeksoi kaikki oppijan puolen lokalisoinnit lokalisaatiopalvelusta"
         (with-access-logging request (ok (indexer/index-all-lokalisoinnit)))))

     (context "/queuer" []
       :tags ["queuer"]

       (POST "/eperusteet" [:as request]
         :summary "Lisää kaikki ePerusteet ja niiden osaamisalat indeksoinnin jonoon"
         (with-access-logging request (ok {:result (queuer/queue-all-eperusteet)})))

       (POST "/oppilaitokset" [:as request]
         :summary "Lisää kaikki aktiiviset oppilaitokset organisaatiopalvelusta  indeksoinnin jonoon"
         (with-access-logging request (ok {:result (queuer/queue-all-oppilaitokset-from-organisaatiopalvelu)})))

       (POST "/osaamismerkit" [:as request]
         :summary "Lisää kaikki osaamismerkit indeksoinnin jonoon"
         (with-access-logging request (ok {:result (queuer/queue-all-osaamismerkit)})))

       (POST "/eperuste" [:as request]
         :summary "Lisää ePeruste ja sen osaamisalat (oid==id)  indeksoinnin jonoon"
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (queuer/queue-eperuste oid)})))

       (POST "/osaamismerkki" [:as request]
         :summary "Lisää osaamismerkin indeksoinnin jonoon"
         :query-params [koodiUri :- String]
         (with-access-logging request (ok {:result (queuer/queue-osaamismerkki koodiUri)})))

       (POST "/oppilaitos" [:as request]
         :summary "Lisää oppilaitos/organisaatio indeksoinnin jonoon"
         :query-params [oid :- String]
         (with-access-logging request (ok {:result (queuer/queue-oppilaitos oid)})))))

   (undocumented
    ;; Static resources path. (resources/public, /public path is implicit for route/resources.)
    (route/resources "/kouta-indeksoija/"))))
