(ns kouta-indeksoija-service.elastic.rebuild-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as cheshire]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.elastic.tools :as t]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer :all]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]
            [clj-elasticsearch.elastic-connect :as e]
            [clj-elasticsearch.elastic-utils :as u]
            [clojure.string :refer [starts-with?]]))

(use-fixtures :once (fn [t] (fixture/restart-elasticsearch t)))

(defonce all-index-names (vec (sort (map first admin/indices-settings-and-mappings))))

(defn ->index-names
  [f]
  (vec (map t/raw-index-name->index-name (f))))

(defn put
  [alias oid]
  (u/elastic-put (u/elastic-url alias "_doc" oid) {:oid oid}))

(defn get-doc
  [alias oid]
  (get-in (e/get-document alias oid) [:_source :oid]))

(defn put->assert-no-write-index
  [alias oid]
  (try
    (put alias oid)
    (is (= true false))
    (catch Exception e
      (is (starts-with?
           (-> e (ex-data) :body (cheshire/parse-string true) (get-in [:error :reason]))
           (str "no write index is defined for alias [" alias "]"))))))

(defn compare-new-and-old-raw-indices
  [old-indices new-indices]
  (doseq [i (range 11)]
    (let [old (nth old-indices i)
          new (nth new-indices i)]
      (is (= (t/raw-index-name->index-name old) (t/raw-index-name->index-name new)))
      (is (not (= old new))))))

(deftest index-rebuild-test
  (testing "Rebuild indices"
    (testing "should initialize all indices in startup"
      (is (admin/initialize-indices))
      (is (= all-index-names (->index-names admin/list-oppija-indices)))
      (is (= (admin/list-oppija-indices) (admin/list-virkailija-indices)))
      (is (= [] (admin/list-unused-indices))))

    (testing "should use initialized indice for virkailija (rw) and oppija (r)"
      (put "koulutus-kouta-virkailija" "1.2.3")
      (put->assert-no-write-index "koulutus-kouta" "1.2.3")
      (is (= "1.2.3" (get-doc "koulutus-kouta-virkailija" "1.2.3")))
      (is (= "1.2.3" (get-doc "koulutus-kouta" "1.2.3"))))

    (testing "should rebuild all indices for virkailija only"
      (let [old-indices (admin/list-virkailija-indices)
            new-indices (sort (admin/initialize-all-indices-for-reindexing))]
        (compare-new-and-old-raw-indices old-indices new-indices)
        (is (= (admin/list-oppija-indices) old-indices))
        (is (= [] (admin/list-unused-indices)))))

    (testing "should use new indices for virkailija only"
      (put "koulutus-kouta-virkailija" "1.2.4")
      (is (nil? (get-doc "koulutus-kouta-virkailija" "1.2.3")))
      (is (= "1.2.4" (get-doc "koulutus-kouta-virkailija" "1.2.4")))
      (is (= "1.2.3" (get-doc "koulutus-kouta" "1.2.3")))
      (is (nil? (get-doc "koulutus-kouta" "1.2.4"))))

    (testing "should synch oppija and virkailija indices correctly"
      (let [old-indices (admin/list-oppija-indices)]
        (admin/sync-all-aliases)
        (is (= (admin/list-oppija-indices) (admin/list-virkailija-indices)))
        (is (= old-indices (admin/list-unused-indices)))))

    (testing "should use new indices for both virkailija (rw) and oppija (r)"
      (put->assert-no-write-index "koulutus-kouta" "1.2.5")
      (put "koulutus-kouta-virkailija" "1.2.5")
      (is (nil? (get-doc "koulutus-kouta-virkailija" "1.2.3")))
      (is (= "1.2.5" (get-doc "koulutus-kouta-virkailija" "1.2.5")))
      (is (= "1.2.5" (get-doc "koulutus-kouta" "1.2.5")))
      (is (nil? (get-doc "koulutus-kouta" "1.2.3"))))

    (testing "should delete unused indices correctly"
      (let [unused-indices (admin/list-unused-indices)
            new-indices (sort (admin/list-virkailija-indices))]
        (compare-new-and-old-raw-indices unused-indices new-indices)
        (admin/delete-unused-indices)
        (is (= all-index-names (->index-names admin/list-oppija-indices)))
        (is (= (admin/list-oppija-indices) (admin/list-virkailija-indices)))
        (is (= [] (admin/list-unused-indices)))))

    (testing "should rebuild some new indices"
      (put "eperuste-virkailija" "123")
      (put "koulutus-kouta-virkailija" "123")

      (put->assert-no-write-index "eperuste" "123")

      (is (= "123" (get-doc "eperuste-virkailija" "123")))
      (is (= "123" (get-doc "eperuste" "123")))
      (is (= "123" (get-doc "koulutus-kouta-virkailija" "123")))
      (is (= "123" (get-doc "koulutus-kouta" "123")))

      (admin/initialize-eperuste-indices-for-reindexing)

      (let [virkailija-indices (admin/list-virkailija-indices)
            oppija-indices (admin/list-oppija-indices)]
        (doseq [i (range 11)]
          (let [old (nth virkailija-indices i)
                new (nth oppija-indices i)]
            (is (= (t/raw-index-name->index-name old) (t/raw-index-name->index-name new)))
            (if (or (= "eperuste" (t/raw-index-name->index-name new))
                    (= "osaamisalakuvaus" (t/raw-index-name->index-name new)))
              (is (not (= old new)))
              (is (= old new))))))

      (is (= all-index-names (->index-names admin/list-oppija-indices)))
      (is (= all-index-names (->index-names admin/list-virkailija-indices)))
      (is (= 19 (count (keys (admin/list-indices-and-aliases)))))

      (is (nil? (get-doc "eperuste-virkailija" "123")))
      (is (= "123" (get-doc "eperuste" "123")))
      (is (= "123" (get-doc "koulutus-kouta-virkailija" "123")))
      (is (= "123" (get-doc "koulutus-kouta" "123"))))

    (testing "should sync new indices for oppija"
      (admin/sync-all-aliases)
      (= (admin/list-virkailija-indices) (admin/list-oppija-indices))
      (is (= (vec (sort (map first admin/eperuste-indices-settings-and-mappings))) (->index-names admin/list-unused-indices)))
      (is (nil? (get-doc "eperuste-virkailija" "123")))
      (is (nil? (get-doc "eperuste" "123")))
      (is (= "123" (get-doc "koulutus-kouta-virkailija" "123")))
      (is (= "123" (get-doc "koulutus-kouta" "123"))))

    (testing "should delete unused indices"
      (admin/delete-unused-indices)
      (is (= [] (admin/list-unused-indices)))
      (= (admin/list-virkailija-indices) (admin/list-oppija-indices))
      (is (= 16 (count (keys (admin/list-indices-and-aliases))))))

    (testing "should not delete lastqueued or indices starting with dot"
      (e/create-index ".kibana" {} {})
      (let [all-indices (admin/list-indices-and-aliases)]
        (is (= true (contains? all-indices :.kibana)))
        (is (= true (contains? all-indices :lastqueued))))
      (is (= [] (admin/list-unused-indices)))
      (is (= nil (admin/delete-unused-indices)))
      (is (e/index-exists ".kibana"))
      (is (e/index-exists "lastqueued")))

    (testing "delete indices by hand should not delete lastqueued, indices starting with dot or indices with aliases"
      (admin/initialize-eperuste-indices-for-reindexing)
      (admin/sync-all-aliases)
      (let [eperuste-indices (->> (admin/list-indices-and-aliases)
                                  (keys)
                                  (map name)
                                  (filter #(clojure.string/starts-with? % "eperuste-"))
                                  (sort)
                                  (vec))]
        (is (= 2 (count eperuste-indices)))
        (let [result (admin/delete-indices [".kibana" "lastqueued" (first eperuste-indices) (second eperuste-indices) "koodisto-01-04-2020-at-12.43.26.241"])]
          (is (= (get result "lastqueued") "Cannot delete index for last queued"))
          (is (= (get result (first eperuste-indices)) true))
          (is (= (get result (second eperuste-indices)) "Cannot delete index with aliases"))
          (is (= (get result "koodisto-01-04-2020-at-12.43.26.241") "Unknown index"))
          (is (= (get result ".kibana") "Cannot delete ElasticSearch index")))))))
