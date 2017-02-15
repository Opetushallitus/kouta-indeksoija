(ns tarjonta-indeksoija-service.organisaatio-client
  (:require [tarjonta-indeksoija-service.conf :refer [env]]
            [tarjonta-indeksoija-service.util.tools :refer [with-error-logging]]
            [clj-http.client :as client]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn get-doc
  [obj]
  (with-error-logging
    (let [url (str (:organisaatio-service-url env) (:oid obj))
          params {:includeImage false}]
      (-> (client/get url {:query-params params :as :json})
          :body))))

(defn- extract-docs [result]
  (->> result
       :body
       :organisaatiot
       (map #(conj (str/split (:parentOidPath %) #"/") (:oid %)))
       flatten
       distinct
       (map #(assoc {} :type "organisaatio" :oid %))))

(defn find-docs
  [oid]
  (with-error-logging
    (let [params {:aktiiviset true :suunnitellut true :lakkautetut true :oid oid}
          url (str (:organisaatio-service-url env) "v2/hae")]
      (extract-docs (client/get url {:query-params params, :as :json})))))