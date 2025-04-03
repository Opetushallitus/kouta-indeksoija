(ns kouta-indeksoija-service.scheduled.jobs
  (:require [clojurewerkz.quartzite.jobs :refer [defjob]]
            [kouta-indeksoija-service.scheduled.scheduler :as scheduler]
            [kouta-indeksoija-service.util.conf :refer [env]]
            [kouta-indeksoija-service.queue.queue :as queue]
            [kouta-indeksoija-service.queue.notification-queue :as notification-queue]
            [kouta-indeksoija-service.queuer.queuer :as queuer]
            [kouta-indeksoija-service.indexer.indexer :as indexer]
            [kouta-indeksoija-service.queuer.last-queued :refer [set-last-queued-time get-last-queued-time]]
            [kouta-indeksoija-service.util.time :refer [long->date-time-string]]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as organisaatio-hierarkia]
            [clojure.tools.logging :as log]))

(def elastic-lock? (atom false :error-handler #(log/error %)))

(defmacro wait-for-elastic-lock
  [& body]
  `(if-not (compare-and-set! elastic-lock? false true)
     (log/debug "Already queueing last changes, skipping job.")
     (try
       (do ~@body)
       (finally (reset! elastic-lock? false)))))

(defjob sqs-job [ctx] (queue/index-from-sqs))

(defonce sqs-job-name "sqs")

(defn schedule-sqs-job
  []
  (scheduler/schedule-one-time-job sqs-job-name sqs-job))

(defn pause-sqs-job
  []
  (scheduler/pause-job sqs-job-name))

(defn resume-sqs-job
  []
  (scheduler/resume-job sqs-job-name))

(defn handle-and-queue-changed-data
  []
  (wait-for-elastic-lock
   (let [now (System/currentTimeMillis)
         execution-id now
         last-modified (get-last-queued-time)
         organisaatio-changes (organisaatio-hierarkia/get-all-muutetut-organisaatiot-cached last-modified)
         org-change-count (count organisaatio-changes)
         eperuste-change-count (queuer/queue-used-or-changed-eperusteet last-modified)
         changes-count (+ eperuste-change-count org-change-count)]
     (when (< 0 org-change-count)
       (organisaatio-hierarkia/clear-hierarkia-cache)
       (try (indexer/index-oppilaitokset organisaatio-changes execution-id false)
            (catch Exception e
              (log/error e "ID: " execution-id " indeksoinnissa tapahtui virhe."))))
     (when (< 0 changes-count)
       (log/info "Fetched and indexed last-modified since" (long->date-time-string last-modified)", containing" changes-count "changes.")
       (set-last-queued-time now)))))

(defjob queueing-job [ctx] (handle-and-queue-changed-data))

(defonce queueing-job-name "queueing")

(defn schedule-queueing-job
  []
  (scheduler/schedule-cron-job queueing-job-name queueing-job (:queueing-cron-string env)))

(defn pause-queueing-job
  []
  (scheduler/pause-job queueing-job-name))

(defn resume-queueing-job
  []
  (scheduler/resume-job queueing-job-name))

(defjob notification-job [ctx] (notification-queue/read-and-send-notifications))

(defonce notification-job-name "notification")

(defn schedule-notification-job
  []
  (scheduler/schedule-one-time-job notification-job-name notification-job))

(defn pause-notification-job
  []
  (scheduler/pause-job notification-job-name))

(defn resume-notification-job
  []
  (scheduler/resume-job notification-job-name))

(defjob lokalisaatio-indexing-job [ctx] (indexer/index-all-lokalisoinnit))

(defonce lokalisaatio-indexing-job-name "lokalisaatio-indexing")

(defn schedule-lokalisaatio-indexing-job
  []
  (scheduler/schedule-cron-job lokalisaatio-indexing-job-name lokalisaatio-indexing-job (:lokalisaatio-indexing-cron-string env)))

(defn pause-lokalisaatio-indexing-job
  []
  (scheduler/pause-job lokalisaatio-indexing-job-name))

(defn resume-lokalisaatio-indexing-job
  []
  (scheduler/resume-job lokalisaatio-indexing-job-name))

(defjob organisaatio-indexing-job [ctx] (indexer/index-all-oppilaitokset))

(defonce organisaatio-indexing-job-name "organisaatio-indexing")

(defn schedule-organisaatio-indexing-job
  []
  (scheduler/schedule-cron-job organisaatio-indexing-job-name organisaatio-indexing-job (:organisaatio-indexing-cron-string env)))

(defjob osaamismerkki-indexing-job [ctx] (indexer/index-all-osaamismerkit))

(defonce osaamismerkki-indexing-job-name "osaamismerkki-indexing")

(defn schedule-osaamismerkki-indexing-job
  []
  (scheduler/schedule-cron-job osaamismerkki-indexing-job-name osaamismerkki-indexing-job (:osaamismerkki-indexing-cron-string env)))

(defn pause-organisaatio-indexing-job
  []
  (scheduler/pause-job organisaatio-indexing-job-name))

(defn resume-organisaatio-indexing-job
  []
  (scheduler/resume-job organisaatio-indexing-job-name))

(defn schedule-jobs
  []
  (schedule-sqs-job)
  (schedule-queueing-job)
  (schedule-notification-job)
  (schedule-lokalisaatio-indexing-job)
  (schedule-organisaatio-indexing-job)
  (schedule-osaamismerkki-indexing-job))

(defn get-jobs-info
  []
  (scheduler/list-scheduled-jobs))

(defn reset-jobs
  []
  (scheduler/reset-scheduler))
