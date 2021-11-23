(ns kouta-indeksoija-service.indexer.indexable
  (:refer-clojure :exclude [get])
  (:require [kouta-indeksoija-service.elastic.tools :as tools]
            [clojure.tools.logging :as log]))

(defn ->index-entry
  ([id doc forwarded-data]
   (when doc (tools/->index-action id doc forwarded-data)))
  ([id doc]
  (->index-entry id doc nil)))

(defn ->delete-entry
  ([id forwarded-data]
   (tools/->delete-action id forwarded-data))
  ([id]
    (->delete-entry id nil)))

(defn- bulk
  [index-name actions]
  (when (not-empty actions)
    (tools/bulk index-name actions)))

(defn- eat-and-log-errors
  [oid f execution-id]
  (try (f oid)
     (catch Exception e
       (log/error e "ID: " execution-id " Indeksoinnissa " oid " tapahtui virhe.")
       nil)))

(defn- create-actions
  [oids f execution-id]
  (flatten (doall (pmap #(eat-and-log-errors % f execution-id) oids))))

(defn do-index
  [index-name oids f execution-id]
  (when-not (empty? oids)
    (let [index-alias (tools/->virkailija-alias index-name)]
      (log/info (str "ID: " execution-id " Indeksoidaan " (count oids) " indeksiin " index-alias ", (o)ids: " (vec oids)))
      (let [start (. System (currentTimeMillis))
            actions (remove nil? (create-actions oids f execution-id))]
        (bulk index-alias (map (fn [action] (dissoc action :forwarded-data)) actions))
        (log/info (str "ID: " execution-id " Indeksointi " index-alias " kesti " (- (. System (currentTimeMillis)) start) " ms."))
        (vec (remove nil? (map :forwarded-data actions)))))))

(defn get
  [index-name oid & query-params]
  (apply tools/get-doc index-name oid query-params))
