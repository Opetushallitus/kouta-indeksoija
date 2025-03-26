(ns kouta-indeksoija-service.queue.conf
  (:refer-clojure :exclude [name])
  (:require [kouta-indeksoija-service.util.conf :refer [env]]
            [clojure.string :refer [blank?]]))

(defn- ->not-blank
  [s]
  (when (not (blank? s))
    s))

(defonce sqs-endpoint (->not-blank (:sqs-endpoint env)))
(defonce sqs-region (->not-blank (:sqs-region env)))
(defonce localstack-aws-access-key-id (->not-blank (:localstack-aws-access-key-id env)))
(defonce localstack-aws-secret-access-key (->not-blank (:localstack-aws-secret-access-key env)))
(defonce test? (parse-boolean (or (:test env) "false")))

(defn priorities
  []
  (keys (:queue env)))

(defn name
  [priority]
  (get-in (:queue env) [(keyword priority) :name]))

(defn health-threshold
  [priority]
  (get-in (:queue env) [(keyword priority) :health-threshold]))