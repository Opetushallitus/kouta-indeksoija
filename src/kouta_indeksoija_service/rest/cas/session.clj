(ns kouta-indeksoija-service.rest.cas.session
  (:refer-clojure :exclude [empty?])
  (:require [kouta-indeksoija-service.rest.util :refer [request handle-error]]
            [kouta-indeksoija-service.rest.cas.session-id :as cas-session-id]
            [clojure.string :refer [upper-case]]
            [clojure.tools.logging :as log]))

(defrecord CasSession [service session-id jsession?])

(defonce error-codes-causing-session-reset [302, 401, 403])

(defn init-session
  [service-url jsession?]
  (let [path (.getPath (java.net.URI. service-url))]
    (log/info "Init cas session to service " path " @ url " service-url)
    (map->CasSession {:service {:url service-url :path path} :session-id (atom nil) :jsession? jsession?})))

(defn- empty?
  [cas-session]
  (let [session-id (:session-id cas-session)]
    (nil? @session-id)))

(defn- reset
  [cas-session]
  (let [session-id (:session-id cas-session)]
    (log/info "Resetting cas session:" cas-session)
    (reset! session-id (cas-session-id/get-id (:service cas-session) (:jsession? cas-session)))))

(defn- assoc-cas-session-params
  [cas-session opts]
  (let [session-id (:session-id cas-session)]
    (-> opts
        (assoc :follow-redirects false :throw-exceptions false)
        (assoc-in [:cookies (if (:jsession? cas-session) "JSESSIONID" "session")] {:value @session-id :path "/"}))))

(defn cas-authenticated-request
  ([cas-session opts]
   (log/info "cas-session" cas-session)
   (when (empty? cas-session)
     (log/info "cas-session EMPTY")
     (reset cas-session))
   (let [http (fn [] (request (assoc-cas-session-params cas-session opts)))
         res (http)]
     (log/info "res" res)
     (if (some #(= % (:status res)) error-codes-causing-session-reset)
       (do (reset cas-session)
           (http))
       res)))
  ([cas-client method url opts]
   (cas-authenticated-request cas-client (assoc opts :url url :method method))))

(defn cas-authenticated-request-as-json
  ([cas-client method url opts]
   (let [method-name (upper-case (str method))]
     (log/info method-name " => " url)
     (log/info opts)
     (try
       (let [response (cas-authenticated-request cas-client method url (assoc opts :as :json :throw-exceptions false))]
         (log/info "response:" response)
         (handle-error url method-name response))
       (catch Exception e (handle-error url method-name e) (throw e)))))
  ([cas-client method url]
   (cas-authenticated-request-as-json cas-client method url {})))
