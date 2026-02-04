(ns kouta-indeksoija-service.elastic.admin
  (:require [cheshire.core :refer [parse-string]]
            [clj-elasticsearch.elastic-connect :as e]
            [clj-elasticsearch.elastic-utils :as u]
            [clj-log.error-log :refer [with-error-logging]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.elastic.settings :refer :all]
            [kouta-indeksoija-service.elastic.tools :as t]
            [kouta-indeksoija-service.indexer.eperuste.eperuste :refer [index-name] :rename {index-name eperuste-index}]
            [kouta-indeksoija-service.indexer.eperuste.osaamisalakuvaus :refer [index-name] :rename {index-name osaamisalakuvaus-index}]
            [kouta-indeksoija-service.indexer.eperuste.tutkinnonosa :refer [index-name] :rename {index-name tutkinnonosa-index}]
            [kouta-indeksoija-service.indexer.eperuste.osaamismerkki :refer [index-name] :rename {index-name osaamismerkki-index}]
            [kouta-indeksoija-service.indexer.koodisto.koodisto :refer [index-name] :rename {index-name koodisto-index}]
            [kouta-indeksoija-service.indexer.kouta.haku :refer [index-name] :rename {index-name haku-index}]
            [kouta-indeksoija-service.indexer.kouta.hakukohde :refer [index-name] :rename {index-name hakukohde-index}]
            [kouta-indeksoija-service.indexer.kouta.koulutus :refer [index-name] :rename {index-name koulutus-index}]
            [kouta-indeksoija-service.indexer.kouta.koulutus-search :refer [index-name] :rename {index-name koulutus-search-index}]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos :refer [index-name] :rename {index-name oppilaitos-index}]
            [kouta-indeksoija-service.indexer.kouta.oppilaitos-search :refer [index-name] :rename {index-name oppilaitos-search-index}]
            [kouta-indeksoija-service.indexer.kouta.sorakuvaus :refer [index-name] :rename {index-name sorakuvaus-index}]
            [kouta-indeksoija-service.indexer.kouta.toteutus :refer [index-name] :rename {index-name toteutus-index}]
            [kouta-indeksoija-service.indexer.kouta.valintaperuste :refer [index-name] :rename {index-name valintaperuste-index}]
            [kouta-indeksoija-service.indexer.lokalisointi.lokalisointi :refer [index-name] :rename {index-name lokalisointi-index}]
            [kouta-indeksoija-service.queuer.last-queued :refer [index-name] :rename {index-name last-queued-index}]))

(defn get-cluster-health
  []
  (e/get-cluster-health))

(defn check-elastic-status
  []
  (log/info "Checking elastic status")
  (with-error-logging
    (e/check-elastic-status)))

(defn get-indices-info
  []
  (e/get-indices-info))

(defn get-elastic-status
  []
  {:cluster_health (:body (get-cluster-health))
   :indices-info (get-indices-info)})

(defn- healthy?
  [x]
  (or (= "green" x)
      (= "yellow" x)))

(defn healthcheck
  []
  (try
    (let [response       (get-elastic-status)
          cluster-health (let [cluster-status (get-in response [:cluster_health :status])
                               cluster-health (healthy? cluster-status)]
                           [cluster-health {:cluster   (get-in response [:cluster_health :cluster_name])
                                            :status    cluster-status
                                            :healthy   cluster-health}])
          indices-health (for [index-info (:indices-info response)
                               :let [index-status (:health index-info)
                                     index-health (healthy? index-status)]]
                           [index-health {:index   (:index index-info)
                                          :status  index-status
                                          :healthy index-health}])]
      [(and (first cluster-health) (not-any? false? (map first indices-health)))
       {:cluster_health (second cluster-health)
        :indices_health (vec (map second indices-health))}])
    (catch Exception e
      (log/error e)
      [false {:error (.getMessage e)}])))

(defn initialize-cluster-settings
  []
  (log/info "Running initialize cluster settings")
  (-> (u/elastic-url "_cluster" "settings")
      (u/elastic-put  {:persistent {:action.auto_create_index "+.*"}})
      :acknowledged))

(defn delete-index
  [index]
  (log/warn "WARNING! Deleting index " index "! All indexed data is lost!")
  {:delete-index (t/delete-index index)})

(defn search [index query]
  (let [res (e/simple-search index query)]
    (if (= 200 (:status res))
      (:body res)
      res)))

(defn- create-index
  [raw-index-name settings mappings]
  (let [result (e/create-index raw-index-name settings mappings)]
    (if (:acknowledged result)
      raw-index-name
      (throw (Exception. (str "Creating index" raw-index-name "failed with result" result))))))

(defn- create-new-index-with-virkailija-alias
  [index-name settings mappings]
  (let [raw-index-name   (t/->raw-index-name index-name)
        virkailija-alias (t/->virkailija-alias index-name)]
    (log/info "Creating new write index" raw-index-name "for alias" virkailija-alias)
    (when (create-index raw-index-name settings mappings)
      (e/move-alias virkailija-alias raw-index-name true)
      (log/info "Index" raw-index-name "created with alias" virkailija-alias)
      raw-index-name)))

(defn- create-new-index-with-virkailija-alias-if-not-exists
  [index-name settings mappings]
  (if-let [write-index (e/find-write-index (t/->virkailija-alias index-name))]
    (log/info "Write index for alias" (t/->virkailija-alias index-name) "exists already:" write-index "No need to create it.")
    (create-new-index-with-virkailija-alias index-name settings mappings)))

(defn- create-new-index-if-not-exists
  [index-name settings mappings]
  (when (not (e/index-exists index-name))
    (create-index index-name settings mappings)
    index-name))

(defonce kouta-indices-settings-and-mappings
  [[koulutus-index index-settings kouta-mappings]
   [toteutus-index index-settings kouta-mappings]
   [hakukohde-index index-settings kouta-mappings]
   [haku-index index-settings kouta-mappings]
   [oppilaitos-index index-settings kouta-mappings]
   [valintaperuste-index index-settings kouta-mappings]
   [sorakuvaus-index index-settings kouta-mappings]
   [koulutus-search-index index-settings-search kouta-search-mappings]
   [oppilaitos-search-index index-settings-search kouta-search-mappings]])

(defonce eperuste-indices-settings-and-mappings
  [[eperuste-index index-settings-eperuste eperuste-mappings]
   [tutkinnonosa-index index-settings eperuste-mappings]
   [osaamisalakuvaus-index index-settings eperuste-mappings]])

(defonce osaamismerkki-indices-settings-and-mappings
  [[osaamismerkki-index index-settings-eperuste osaamismerkki-mappings]])

(defonce koodisto-indices-settings-and-mappings
  [[koodisto-index index-settings koodisto-mappings]])

(defonce lokalisointi-indices-settings-and-mappings
  [[lokalisointi-index index-settings-lokalisointi lokalisointi-mappings]])

(defonce indices-settings-and-mappings
  (into [] (concat kouta-indices-settings-and-mappings
                   eperuste-indices-settings-and-mappings
                   osaamismerkki-indices-settings-and-mappings
                   koodisto-indices-settings-and-mappings
                   lokalisointi-indices-settings-and-mappings)))

(defn initialize-indices
  []
  (try
    (create-new-index-if-not-exists last-queued-index index-settings nil)
    (doseq [[index settings mappings] indices-settings-and-mappings]
      (when-let [raw-index-name (create-new-index-with-virkailija-alias-if-not-exists index settings mappings)]
        (e/move-alias (t/->oppija-alias index) raw-index-name false)
        raw-index-name))
    true
    (catch Exception e
      (log/error "Unable to create indices in startup" e)
      false)))

(defn- initialize-new-indices-for-reindexing
  [indices-with-settings-and-mappings]
  (vec
   (for [[index settings mappings] indices-with-settings-and-mappings]
     (create-new-index-with-virkailija-alias index settings mappings))))

(defn initialize-all-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing indices-settings-and-mappings))

(defn initialize-kouta-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing kouta-indices-settings-and-mappings))

(defn initialize-eperuste-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing eperuste-indices-settings-and-mappings))

(defn initialize-osaamismerkki-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing osaamismerkki-indices-settings-and-mappings))

(defn initialize-koodisto-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing koodisto-indices-settings-and-mappings))

(defn initialize-lokalisointi-indices-for-reindexing
  []
  (initialize-new-indices-for-reindexing lokalisointi-indices-settings-and-mappings))

(defn initialize-new-index-for-reindexing
  [index-name]
  (if-let [[index settings mappings] (first (filter #(= index-name (first %)) indices-settings-and-mappings))]
    (create-new-index-with-virkailija-alias index settings mappings)
    (throw (IllegalArgumentException. (str "Unknown index name \"" index-name "\". Valid index names are " (vec (map first indices-settings-and-mappings)))))))

(defn list-indices-and-aliases
  []
  (into (sorted-map) (e/list-aliases)))

(defn- has-alias?
  [all-indices-with-aliases index]
  (not-empty (keys (get-in all-indices-with-aliases [(keyword index) :aliases]))))

(defn- last-queued-index?
  [index]
  (= last-queued-index (name index)))

(defn- elasticsearch-index?
  [index]
  (string/starts-with? (name index) "."))

(defn delete-indices
  [indices]
  (apply merge-with {}
         (for [index indices]
           (let [all-indices-with-aliases (list-indices-and-aliases)
                 exists? #(contains? all-indices-with-aliases (keyword %))
                 alias? (partial has-alias? all-indices-with-aliases)]
             (cond
               (not (exists? index))        {index "Unknown index"}
               (alias? index)               {index "Cannot delete index with aliases"}
               (last-queued-index? index)   {index "Cannot delete index for last queued"}
               (elasticsearch-index? index) {index "Cannot delete ElasticSearch index"}
               :else                        {index (-> (e/delete-index (name index))
                                                       :body
                                                       (parse-string)
                                                       (get "acknowledged"))})))))

(defn- list-and-filter-indices
  [f]
  (->> (let [all-indices-with-aliases (list-indices-and-aliases)]
         (for [index (keys all-indices-with-aliases)]
           (when (and (not (or (last-queued-index? index) (elasticsearch-index? index)))
                      (f (get-in all-indices-with-aliases [index :aliases])))
             index)))
       (remove nil?)
       (map name)
       (sort)
       (vec)))

(defn list-unused-indices
  []
  (list-and-filter-indices #(empty? (keys %))))

(defn list-virkailija-indices
  []
  (list-and-filter-indices #(seq (filter t/virkailija-alias? (keys %)))))

(defn list-oppija-indices
  []
  (list-and-filter-indices #(seq (filter t/oppija-alias? (keys %)))))

(defn delete-unused-indices
  []
  (delete-indices (list-unused-indices)))

(defn list-indices-with-alias
  [alias]
  (e/list-indices-with-alias alias))

(defn sync-all-aliases
  []
  (vec
   (for [[index] indices-settings-and-mappings]
     (let [real-index (e/move-read-alias-to-write-index (t/->virkailija-alias index) (t/->oppija-alias index))]
       (log/info "Moved" (t/->oppija-alias index) "to" real-index)
       {:alias (t/->oppija-alias index)
        :index real-index}))))
