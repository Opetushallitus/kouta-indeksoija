(ns kouta-indeksoija-service.util.time
  (:require [clj-time.format :as format]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import (java.util Locale)))

(defonce timezone-fi (time/time-zone-for-id "Europe/Helsinki"))
(defonce timezone-utc (time/time-zone-for-id "UTC"))

(defn formatter-for-helsinki [fmt-str] (format/formatter fmt-str timezone-fi))
(defn formatter-for-utc [fmt-str] (format/formatter fmt-str timezone-utc))

(defonce formatter-with-seconds (formatter-for-helsinki "yyyy-MM-dd'T'HH:mm:ss"))

(defonce formatter-with-time (format/with-zone (format/formatter "yyyy-MM-dd HH:mm") (time/default-time-zone)))

(defonce formatter-rfc1123 (format/formatter "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))

(defonce formatter-index-time-postfix (format/formatter "dd-MM-yyyy-'at'-HH.mm.ss.SSS"))

(defonce finnish-format (formatter-for-helsinki "d.M.yyyy 'klo' HH:mm"))
(defonce swedish-format (formatter-for-helsinki "d.M.yyyy 'kl.' HH:mm"))
; Asetetaan US-locale, jotta AM/PM olisi järjestelmällisesti kirjoitettu isoilla kirjaimilla.
(defonce english-format (-> (formatter-for-helsinki "MMM. d, yyyy 'at' hh:mm a z") (.withLocale Locale/US)))

(defn date-is-before-now? [date-str]
  (let [now (time/now)
        comp (format/parse date-str)]
    (time/before? comp now)))

(defn long->date-time
  [long]
  (coerce/from-long long))

(defn date-time->date-time-string
  ([datetime formatter]
   (format/unparse formatter datetime))
  ([datetime]
   (date-time->date-time-string datetime formatter-with-time)))

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
  (long->index-postfix-time (coerce/to-long (time/now))))

(defn long->rfc1123
  [long]
  (format/unparse formatter-rfc1123 (long->date-time long)))

(defn parse-utc-date-time [date-str]
  (let [fmt (formatter-for-utc "yyyy-MM-dd'T'HH:mm")]
   (format/parse fmt date-str)))

(defn parse-date-time
  [date-str]
  (let [fmt (formatter-for-helsinki "yyyy-MM-dd'T'HH:mm")]
    (try
      (time/to-time-zone (format/parse formatter-with-seconds date-str) timezone-fi)
      (catch Exception _
        (try
          (time/to-time-zone (format/parse fmt date-str) timezone-fi)
          (catch Exception e
            (log/error (str "Unable to parse" date-str) e)))))))

(defn- replace-eet-eest-with-utc-offset [parse-date-time]
  (-> parse-date-time
      (string/replace #"EET" "UTC+2")
      (string/replace #"EEST" "UTC+3")))

(defn format-localized-date [date]
  (if-let [parsed (parse-date-time date)]
    {:fi (format/unparse finnish-format parsed)
     :sv (format/unparse swedish-format parsed)
     :en (replace-eet-eest-with-utc-offset (format/unparse english-format parsed))}
    {}))

(defn year [date] (time/year date))
(defn month [date] (time/month date))

(defn start-of-year [year]
  (time/from-time-zone (time/date-time year 1 1) timezone-fi))

(defn before? [date-str date-time]
  (time/before? (parse-date-time date-str) date-time))

(defn kevat-date? [date] (< (month date) 8))
