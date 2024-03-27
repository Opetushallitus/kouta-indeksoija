(ns kouta-indeksoija-service.elastic.admin-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.elastic.settings :as settings]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.test-tools :refer [debug-pretty]]
            [clj-elasticsearch.elastic-utils :refer [elastic-url elastic-get]]
            [clj-http.client :as http]
            [clojure.string :refer [starts-with?]]))

(use-fixtures :once (fn [t] (fixture/restart-elasticsearch t)))

(defn- find-hakukohde-index
  []
  (->> (admin/list-indices-and-aliases)
       (keys)
       (map name)
       (filter #(starts-with? % "hakukohde-kouta"))
       (first)))

(deftest elastic-admin-test
  (testing "Elastic admin"
    (testing "should check that elastic search is alive"
      (is (admin/check-elastic-status)))

    (testing "should initialize indices"
      (is (admin/initialize-indices)))

    (testing "should have index analyzer settings set"
      (let [index (find-hakukohde-index)
            res  (http/get (elastic-url index "_settings") {:as :json :content-type :json})]
        (is (= (:analysis settings/index-settings)
               (get-in res [:body (keyword index) :settings :index :analysis])))))

    (testing "should have index stemmer settings set"
      (let [index (find-hakukohde-index)
            res (http/get (elastic-url index "_mappings") {:as :json :content-type :json})]
        (is (= settings/kouta-mappings
               (get-in res [:body (keyword index) :mappings])))))

    (testing "should get elastic-status"
      (is (= [:cluster_health :indices-info] (keys (admin/get-elastic-status)))))

    (testing "get cluster health"
      (is (= true (first (admin/healthcheck)))))

    (testing "auto create index settings is correctly set"
      (is (= {} (-> (elastic-url "_cluster" "settings") (elastic-get) :persistent)))
      (is (admin/initialize-cluster-settings))
      (is (= {:action {:auto_create_index "+.*"}} (-> (elastic-url "_cluster" "settings") (elastic-get) :persistent))))))
