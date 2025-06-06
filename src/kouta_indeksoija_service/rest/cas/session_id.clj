(ns kouta-indeksoija-service.rest.cas.session-id
  (:require [kouta-indeksoija-service.util.conf :refer [env]]
            [kouta-indeksoija-service.util.urls :refer [resolve-url]]
            [kouta-indeksoija-service.rest.util :refer [request]]
            [jsoup.soup :refer :all]
            [clojure.string :refer [blank?]]))

(defn- send-form
  [url form]
  (request {:form-params      form
            :method           :post
            :url              url
            :throw-exceptions false
            :content-type     "application/x-www-form-urlencoded"}))

(defn- parse-ticket-granting-ticket
  [response]
  (let [parsed-body (-> response :body parse)]
    (first (attr "action" ($ parsed-body "form")))))

(defn- get-ticket-granting-ticket
  []
  (let [username (-> env :cas :username)
        password (-> env :cas :password)
        response (send-form (resolve-url :cas.v1.tickets) {:username username :password password})
        tgt (parse-ticket-granting-ticket response)]
    (if (blank? tgt)
      (throw (RuntimeException. (format "Unable to read tgt on CAS response: %s" response)))
      tgt)))

(defn- get-service-ticket
  [service-url tgt]
  (let [response (send-form tgt {:service service-url})]
    (if-let [st (:body response)]
      st
      (throw (RuntimeException. (format "Unable to parse service ticket for service %s on responce: %s!" service-url response))))))

(defn- parse-jsession-id
  [response]
  (or (when-let [cookie (-> response :headers :set-cookie)]
        (nth (re-find #"JSESSIONID=(\w*);" cookie) 1 nil))
      (some-> response :cookies (get "JSESSIONID") :value)))

(defn- get-jsession-id
  [service-url st]
  (let [response (request {:headers {"CasSecurityTicket" st} :url service-url :method :get :throw-exceptions false})]
    (if-let [jsession-id (parse-jsession-id response)]
      jsession-id
      (throw (RuntimeException. (format "Unable to parse session ID from %s on response: %s" service-url response))))))

(defn- get-session-id
  [service-url st]
  (let [url (str service-url "?ticket=" st)
        response (request {:url url :method :get :throw-exceptions false :follow-redirects false})]
    (if-let [session-id (-> response :cookies (get "session") :value)]
      session-id
      (throw (RuntimeException. (format "Unable to parse session ID! Uri = %s and response %s" url response))))))

(defn get-id
  [service jsession?]
  (if jsession?
    (->> (get-ticket-granting-ticket)
         (get-service-ticket (str (:url service) "/j_spring_cas_security_check"))
         (get-jsession-id (:url service)))
    (->> (get-ticket-granting-ticket)
         (get-service-ticket (:url service))
         (get-session-id (:url service)))))