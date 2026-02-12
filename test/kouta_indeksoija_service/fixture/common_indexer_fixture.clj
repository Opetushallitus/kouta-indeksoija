(ns kouta-indeksoija-service.fixture.common-indexer-fixture
  (:require
   [cheshire.core :as cheshire]
   [clojure.test :refer :all]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [kouta-indeksoija-service.fixture.common-oids :refer :all]
   [kouta-indeksoija-service.elastic.tools :refer [get-doc]]
   [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
   [kouta-indeksoija-service.elastic.admin :as admin]
   [kouta-indeksoija-service.indexer.kouta.haku :as haku]
   [kouta-indeksoija-service.indexer.kouta.hakukohde :as hakukohde]
   [kouta-indeksoija-service.indexer.kouta.koulutus :as koulutus]
   [kouta-indeksoija-service.indexer.kouta.koulutus-search :as koulutus-search]
   [kouta-indeksoija-service.indexer.kouta.oppilaitos :as oppilaitos]
   [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :as oppilaitos-search]
   [kouta-indeksoija-service.indexer.kouta.toteutus :as toteutus]
   [kouta-indeksoija-service.indexer.kouta.valintaperuste :as valintaperuste])
  (:import (java.time LocalDate LocalTime)
           (java.time.format DateTimeFormatter)))

(defn no-formatoitu-date
  [json]
  (postwalk (fn [m]
              (if (map? m)
                (loop [ks (keys m)
                       mm m]
                  (if-let [k (and (first ks) (name (first ks)))]
                    (if (string/starts-with? k "formatoitu")
                      (recur (rest ks)
                             (dissoc mm (first ks)))
                      (recur (rest ks)
                             mm))
                    mm))
                m)) json))

(defn no-timestamp
  [json]
  (dissoc (no-formatoitu-date json) :timestamp))

(defn replace-alkamiskausi
  [json-string]
  (string/replace json-string "!!tämän-vuoden-kevät" (str (-> (LocalDate/now)
                                                              (.getYear)
                                                              (.toString)) "-kevat")))
(defonce formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm"))

(defn test-date
  [time days-in-future]
  (let [read-time (LocalTime/parse time)
        test-date (-> (LocalDate/now)
                      (.atTime read-time)
                      (.plusDays days-in-future))]
    (.format formatter test-date)))

(def far-enough-in-the-future-start-time "2042-03-24T09:49")
(def far-enough-in-the-future-end-time "2042-03-29T09:49")

(defn get-kausi
  [month]
  (if (<= month 7) 
    "kausi_k#1" "kausi_s#1"))

(defn replace-times
  [json-string]
  (-> json-string
      (string/replace "!!startTime1hc" far-enough-in-the-future-start-time)
      (string/replace "!!endTime1hc" far-enough-in-the-future-end-time)
      (string/replace "!!startTime1" (test-date "09:49" 1))
      (string/replace "!!endTime1" (test-date "09:58" 1))
      (string/replace "!!time3" (test-date "09:58" 3))
      (string/replace "!!thisYear" (-> (LocalDate/now)
                                       (.getYear)
                                       (.toString)))
      (string/replace "!!thisKausi" (get-kausi (-> (LocalDate/now)
                                                   (.getMonthValue))))))

(defn read-json-as-string
  ([path name]
   (-> (str path name ".json")
       (slurp)
       (replace-times)))
  ([name]
   (read-json-as-string "test/resources/kouta/" name)))

(defn parse-json
  [json]
  (cheshire/parse-string json true))

(defn json
  ([path name]
   (parse-json (read-json-as-string path name)))
  ([name]
   (json "test/resources/kouta/" name)))

(defn check-all-nil
  []
  (is (nil? (get-doc koulutus-search/index-name koulutus-oid)))
  (is (nil? (get-doc koulutus/index-name koulutus-oid)))
  (is (nil? (get-doc koulutus/index-name koulutus-oid2)))
  (is (nil? (get-doc toteutus/index-name toteutus-oid)))
  (is (nil? (get-doc haku/index-name haku-oid)))
  (is (nil? (get-doc haku/index-name ei-julkaistu-haku-oid)))
  (is (nil? (get-doc hakukohde/index-name hakukohde-oid)))
  (is (nil? (get-doc hakukohde/index-name hakukohde-oid2)))
  (is (nil? (get-doc hakukohde/index-name ei-julkaistun-haun-julkaistu-hakukohde-oid)))
  (is (nil? (get-doc valintaperuste/index-name valintaperuste-id)))
  (is (nil? (get-doc oppilaitos/index-name oppilaitos-oid)))
  (is (nil? (get-doc oppilaitos-search/index-name oppilaitos-oid))))

(defn filter-search-terms-by-key
  [search-index oid key expected]
  (filter #(= expected (get % key)) (:search_terms (get-doc search-index oid))))

(defn count-search-terms-by-key
  [search-index oid key expected]
  (count (filter-search-terms-by-key search-index oid key expected)))

(defn search-terms-key-not-empty
  [search-index oid key]
  (some? (seq (some (fn [h] (get h key)) (:search_terms (get-doc search-index oid))))))

(defn- add-mock-kouta-data
  []
  (fixture/add-koulutus-mock koulutus-oid
                             :tila "julkaistu"
                             :nimi "Autoalan perustutkinto 0"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :sorakuvausId sorakuvaus-id
                             :julkinen true
                             :modified "2019-01-31T09:11:23"
                             :tarjoajat [oppilaitos-oid])

  (fixture/add-koulutus-mock koulutus-oid2
                             :tila "tallennettu"
                             :nimi "Autoalan perustutkinto 1"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :sorakuvausId sorakuvaus-id
                             :julkinen "true"
                             :modified "2021-11-16T08:55:23"
                             :tarjoajat [oppilaitos-oid3])

  (fixture/add-toteutus-mock toteutus-oid
                             koulutus-oid
                             :tila "arkistoitu"
                             :nimi "Koulutuksen 0 toteutus 0"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :modified "2019-02-01T13:16:23"
                             :tarjoajat [oppilaitoksen-osa-oid oppilaitoksen-osa-oid2])

  (fixture/add-toteutus-mock toteutus-oid2
                             koulutus-oid
                             :tila "julkaistu"
                             :nimi "Koulutuksen 0 toteutus 1"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :modified "2019-02-01T13:16:23"
                             :tarjoajat [oppilaitoksen-osa-oid])

  (fixture/add-toteutus-mock toteutus-oid3
                             koulutus-oid
                             :tila "julkaistu"
                             :nimi "Koulutuksen 0 toteutus 2"
                             :muokkaaja "1.2.246.562.24.62301161440"
                             :modified "2019-02-01T13:16:23"
                             :tarjoajat [oppilaitoksen-osa-oid2])

  (fixture/add-haku-mock haku-oid
                         :tila "julkaistu"
                         :nimi "Haku 0"
                         :muokkaaja "1.2.246.562.24.62301161440"
                         :modified "2019-02-05T09:49:23")

  (fixture/add-hakukohde-mock hakukohde-oid
                              toteutus-oid
                              haku-oid
                              :tila "arkistoitu"
                              :valintaperuste valintaperuste-id
                              :nimi "Koulutuksen 0 toteutuksen 0 hakukohde 0"
                              :esitysnimi "Koulutuksen 0 toteutuksen 0 hakukohteen 0 esitysnimi"
                              :muokkaaja "1.2.246.562.24.62301161440"
                              :modified "2019-02-05T09:49:23"
                              :jarjestyspaikkaOid default-jarjestyspaikka-oid)

  (fixture/add-hakukohde-mock hakukohde-oid2
                              toteutus-oid3
                              haku-oid
                              :tila "julkaistu"
                              :valintaperuste valintaperuste-id
                              :nimi "Koulutuksen 0 toteutuksen 2 hakukohde 0"
                              :esitysnimi "Koulutuksen 0 toteutuksen 2 hakukohteen 2 esitysnimi"
                              :muokkaaja "1.2.246.562.24.62301161440"
                              :hakuaikaAlkaa "2018-10-10T12:00"
                              :hakuaikaPaattyy "2030-11-10T12:00"
                              :modified "2019-02-05T09:49:23"
                              :jarjestyspaikkaOid default-jarjestyspaikka-oid)

  (fixture/add-haku-mock ei-julkaistu-haku-oid
                         :tila "tallennettu"
                         :nimi "Ei julkaistu haku"
                         :muokkaaja "1.2.246.562.24.62301161440"
                         :modified "2021-10-27T14:44:44")

  (fixture/add-hakukohde-mock ei-julkaistun-haun-julkaistu-hakukohde-oid
                              toteutus-oid3
                              ei-julkaistu-haku-oid
                              :tila "julkaistu"
                              :valintaperuste valintaperuste-id
                              :nimi "Ei julkaistun haun julkaistu hakukohde"
                              :muokkaaja "1.2.246.562.24.62301161440"
                              :hakuaikaAlkaa "2022-10-10T12:00"
                              :hakuaikaPaattyy "2080-11-10T12:00"
                              :modified "2021-10-27T14:44:44"
                              :jarjestyspaikkaOid oppilaitos-oid)

  (fixture/add-sorakuvaus-mock sorakuvaus-id
                               :tila "arkistoitu"
                               :nimi "Sorakuvaus 0"
                               :muokkaaja "1.2.246.562.24.62301161440"
                               :modified "2019-02-05T09:49:23")

  (fixture/add-valintaperuste-mock valintaperuste-id
                                   :tila "arkistoitu"
                                   :nimi "Valintaperuste 0"
                                   :muokkaaja "1.2.246.562.24.62301161440"
                                   :modified "2019-02-05T09:49:23")

  (fixture/add-oppilaitos-mock oppilaitos-oid
                               :tila "julkaistu"
                               :muokkaaja "1.2.246.562.24.62301161440"
                               :modified "2019-02-05T09:49:23")

  (fixture/add-oppilaitoksen-osa-mock oppilaitoksen-osa-oid
                                      oppilaitos-oid
                                      :tila "julkaistu"
                                      :muokkaaja "1.2.246.562.24.62301161440"
                                      :modified "2019-02-05T09:49:23")

  (fixture/add-oppilaitoksen-osa-mock
   (let [organisaatio-with-children (fixture/->keywordized-json (slurp "test/resources/organisaatiot/1.2.246.562.10.10101010102-kouta.json"))]
     (-> fixture/default-oppilaitos-map
         (merge {:organisaatio oppilaitoksen-osa-oid2
                 :oid oppilaitos-oid2
                 :tila "julkaistu"
                 :muokkaaja "1.2.246.562.24.62301161440"
                 :modified "2019-02-05T09:49:23"})
         (assoc-in [:_enrichedData :organisaatio] organisaatio-with-children))))

  (fixture/add-oppilaitos-mock
   (let [organisaatio-with-children (fixture/->keywordized-json (slurp "test/resources/organisaatiot/1.2.246.562.10.10101010101-kouta.json"))]
     (-> fixture/default-oppilaitos-map
         (merge {:organisaatio oppilaitos-oid
                 :oid oppilaitos-oid2
                 :tila "julkaistu"
                 :muokkaaja "1.2.246.562.24.62301161440"
                 :modified "2019-02-05T09:49:23"})
         (assoc-in [:_enrichedData :organisaatio] organisaatio-with-children))))

  (fixture/add-oppilaitoksen-osa-mock oppilaitoksen2-osa-oid
                                      oppilaitos-oid2
                                      :tila "julkaistu"
                                      :muokkaaja "1.2.246.562.24.62301161440"
                                      :modified "2019-02-05T09:49:23"))

(defn common-indexer-fixture
  [tests]
  (fixture/teardown)
  (admin/initialize-indices)
  (add-mock-kouta-data)
  (tests))
