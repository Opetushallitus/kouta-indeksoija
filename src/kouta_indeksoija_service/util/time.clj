(ns kouta-indeksoija-service.util.time
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import (java.util Locale)
           (java.time Instant LocalDate ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defonce timezone-fi (ZoneId/of "Europe/Helsinki"))
(defonce timezone-utc (ZoneId/of "UTC"))

(defn formatter-for-helsinki [fmt-str] (-> (DateTimeFormatter/ofPattern fmt-str) (.withZone timezone-fi)))
(defn formatter-for-utc [fmt-str] (-> (DateTimeFormatter/ofPattern fmt-str) (.withZone timezone-utc)))

(defonce formatter-with-seconds (formatter-for-helsinki "yyyy-MM-dd'T'HH:mm:ss"))

(defonce formatter-with-time (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm") (.withZone timezone-fi)))

(defonce formatter-rfc1123 (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))

(defonce formatter-local-date (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(defonce formatter-index-time-postfix (DateTimeFormatter/ofPattern "dd-MM-yyyy-'at'-HH.mm.ss.SSS"))

(defonce finnish-format (formatter-for-helsinki "d.M.yyyy 'klo' HH:mm"))
(defonce swedish-format (formatter-for-helsinki "d.M.yyyy 'kl.' HH:mm"))
; Asetetaan US-locale, jotta AM/PM olisi järjestelmällisesti kirjoitettu isoilla kirjaimilla.
(defonce english-format (-> (formatter-for-helsinki "MMM. d, yyyy 'at' hh:mm a z") (.withLocale Locale/US)))

(defn current-time-millis
  "Funktio tämänhetkisen ajan hakemiseen, jotta tämän voi ylikirjoittaa testeissä."
  ^Long [] (System/currentTimeMillis))

(defn current-local-date
  "Funktio nykyisen päivän hakemiseen, jotta tämän voi ylikirjoittaa testeissä."
  ^LocalDate [] (LocalDate/now (ZoneId/of "Europe/Helsinki")))

(defn date-is-before-now?
  "Palauttaa true, jos kysytty päivä on kokonaan menneisyydessä."
  [date-str]
  (let [now (current-local-date)
        comp (LocalDate/parse date-str formatter-local-date)]
    (.isBefore comp now)))

(defn date-time->date-time-string
  ([^ZonedDateTime datetime ^DateTimeFormatter formatter]
   (.format formatter datetime))
  ([datetime]
   (date-time->date-time-string datetime formatter-with-time)))

(defn long->date-time
  ^ZonedDateTime [millis]
  (ZonedDateTime/ofInstant (Instant/ofEpochMilli millis) timezone-utc))

(defn long->date-time-string
  ([long]
   (date-time->date-time-string (long->date-time long)))
  ([long formatter]
   (date-time->date-time-string (long->date-time long) formatter)))

(defn long->indexed-date-time
  [long]
  (long->date-time-string long formatter-with-seconds))

(defn long->index-postfix-time [millis]
  (long->date-time-string millis formatter-index-time-postfix))

(defn current-index-postfix-time []
  (long->index-postfix-time (current-time-millis)))

(defn long->rfc1123
  [long]
  (.format formatter-rfc1123 (long->date-time long)))

(defn parse-utc-date-time ^ZonedDateTime [date-str]
  (let [fmt (formatter-for-utc "yyyy-MM-dd'T'HH:mm")]
   (ZonedDateTime/parse date-str fmt)))

(defn parse-date-time
  ^ZonedDateTime [date-str]
  (let [fmt (formatter-for-helsinki "yyyy-MM-dd'T'HH:mm")]
    (try
      (ZonedDateTime/parse date-str formatter-with-seconds)
      (catch Exception _
        (try
          (ZonedDateTime/parse date-str fmt)
          (catch Exception e
            (log/error (str "Unable to parse" date-str) e)))))))

(defn- replace-eet-eest-with-utc-offset [parse-date-time]
  (-> parse-date-time
      (string/replace #"EET" "UTC+2")
      (string/replace #"EEST" "UTC+3")))

(defn format-localized-date [date]
  (if-let [parsed (parse-date-time date)]
    {:fi (.format finnish-format parsed)
     :sv (.format swedish-format parsed)
     :en (replace-eet-eest-with-utc-offset (.format english-format parsed))}
    {}))

(defn year [date] (.getYear date))
(defn month [date] (.getMonthValue date))

(defn start-of-year [^long year]
  (.atStartOfDay (LocalDate/of year 1 1) timezone-fi))

(defn before? [date-str date-time]
  (.isBefore (parse-date-time date-str) date-time))

(defn kevat-date? [date] (< (month date) 8))
