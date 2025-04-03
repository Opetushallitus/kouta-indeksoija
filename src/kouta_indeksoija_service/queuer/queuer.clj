(ns kouta-indeksoija-service.queuer.queuer
  (:require [kouta-indeksoija-service.indexer.cache.hierarkia :as organisaatio-cache]
            [kouta-indeksoija-service.rest.eperuste :as eperusteet-client]
            [kouta-indeksoija-service.rest.osaamismerkki :as osaamismerkki-client]
            [kouta-indeksoija-service.rest.kouta :as kouta-client]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.queue.sqs :as sqs]))

(def elastic-lock? (atom false :error-handler #(log/error %)))

(defn- queue
  [& {:keys [oppilaitokset eperusteet osaamismerkit] :or {oppilaitokset [] eperusteet [] osaamismerkit []}}]
  (sqs/send-message
   (sqs/queue :fast)
   (cond-> {}
     (not-empty oppilaitokset) (assoc :oppilaitokset (vec oppilaitokset))
     (not-empty eperusteet) (assoc :eperusteet (vec eperusteet))
     (not-empty osaamismerkit) (assoc :osaamismerkit (vec osaamismerkit)))))

(defn queue-all-eperusteet
  []
  (let [all-eperusteet (eperusteet-client/find-all)]
    (doseq [eperusteet (partition-all 20 all-eperusteet)]
      (queue :eperusteet eperusteet))))

(defn queue-eperuste
  [oid]
  (queue :eperusteet [oid]))

(defn queue-all-osaamismerkit
  []
  (let [all-osaamismerkit (osaamismerkki-client/fetch-all)]
    (doseq [osaamismerkit (partition-all 20 all-osaamismerkit)]
      (queue :osaamismerkit osaamismerkit))))

(defn queue-osaamismerkki
  [osaamismerkki-koodi-uri]
  (queue :osaamismerkit [osaamismerkki-koodi-uri]))

(defn queue-all-oppilaitokset-from-organisaatiopalvelu
  []
  (organisaatio-cache/clear-hierarkia-cache)
  (let [all-oppilaitokset (organisaatio-cache/get-all-indexable-oppilaitos-oids)]
    (log/info (str "Lisätään jonoon " (count all-oppilaitokset) " oppilaitosta"))
    (doseq [oppilaitokset (partition-all 20 all-oppilaitokset)]
      (queue :oppilaitokset oppilaitokset))))

(defn queue-oppilaitos
  [oid]
  (queue :oppilaitokset [oid]))

(defn queue-used-or-changed-eperusteet
  [last-modified]
  (let [eperuste-changes (eperusteet-client/find-changes last-modified)
        queued-eperusteet (set (concat eperuste-changes (kouta-client/list-used-eperuste-ids-with-cache)))
        queued-count (count queued-eperusteet)]
    (when (< 0 queued-count)
      (queue :eperusteet queued-eperusteet))
     queued-count))
