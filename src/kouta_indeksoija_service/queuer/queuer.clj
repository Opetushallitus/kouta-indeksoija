(ns kouta-indeksoija-service.queuer.queuer
  (:require [kouta-indeksoija-service.indexer.cache.hierarkia :as organisaatio-cache]
            [kouta-indeksoija-service.rest.eperuste :as eperusteet-client]
            [clojure.tools.logging :as log]
            [kouta-indeksoija-service.queue.sqs :as sqs]))

(def elastic-lock? (atom false :error-handler #(log/error %)))

(defn- queue
  [& {:keys [oppilaitokset eperusteet] :or {oppilaitokset [], eperusteet []}}]
  (sqs/send-message
   (sqs/queue :fast)
   (cond-> {}
           (not-empty oppilaitokset) (assoc :oppilaitokset (vec oppilaitokset))
           (not-empty eperusteet) (assoc :eperusteet (vec eperusteet)))))

(defn queue-all-eperusteet
  []
  (let [all-eperusteet (eperusteet-client/find-all)]
    (doseq [eperusteet (partition-all 20 all-eperusteet)]
      (queue :eperusteet eperusteet))))

(defn queue-eperuste
  [oid]
  (queue :eperusteet [oid]))

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

(defn queue-eperuste-changes
  [last-modified]
  (let [eperuste-changes (eperusteet-client/find-changes last-modified)
        change-count (count eperuste-changes)]
    (when (< 0 change-count)
      (queue :eperusteet eperuste-changes))
     change-count))
