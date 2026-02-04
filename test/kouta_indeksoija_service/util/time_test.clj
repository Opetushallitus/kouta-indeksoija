(ns kouta-indeksoija-service.util.time-test
  (:require [clojure.test :refer [deftest testing is]]
            [kouta-indeksoija-service.util.time :as time])
  (:import (java.time LocalDate ZonedDateTime)))

(defonce exact-time 1770112704312)
(defonce ten-pm-in-helsinki 1735675200000)
(defonce midnight-in-helsinki 1735682400000)
(defonce midnight-in-utc 1735689600000)
(defonce two-o-clock-in-utc 1735696800000)

(defn- breakdown
  "Palauttaa ajan pilkottuna osiin: [vuosi kk pv t m s milli vyöhyke]"
  [^ZonedDateTime time]
  [(.getYear time)
   (.getMonthValue time)
   (.getDayOfMonth time)
   (.getHour time)
   (.getMinute time)
   (.getSecond time)
   (-> time .getNano (/ 1000000) int)
   (-> time .getZone .getId)])

(deftest long->rfc1123
  (testing "returns correct timestamp in GMT time"
    (is (= "Tue, 03 Feb 2026 09:58:24 GMT" (time/long->rfc1123 exact-time)))
    (is (= "Tue, 31 Dec 2024 22:00:00 GMT" (time/long->rfc1123 midnight-in-helsinki)))
    (is (= "Wed, 01 Jan 2025 00:00:00 GMT" (time/long->rfc1123 midnight-in-utc)))))

(deftest long->indexed-date-time
  (testing "returns correct timestamp in Helsinki time"
    (is (= "2026-02-03T11:58:24" (time/long->indexed-date-time exact-time)))
    (is (= "2024-12-31T22:00:00" (time/long->indexed-date-time ten-pm-in-helsinki)))
    (is (= "2025-01-01T00:00:00" (time/long->indexed-date-time midnight-in-helsinki)))
    (is (= "2025-01-01T02:00:00" (time/long->indexed-date-time midnight-in-utc)))))

(deftest long->date-time-string
  (testing "returns correct timestamp in Helsinki time"
    (is (= "2026-02-03 11:58" (time/long->date-time-string exact-time)))
    (is (= "2024-12-31 22:00" (time/long->date-time-string ten-pm-in-helsinki)))
    (is (= "2025-01-01 00:00" (time/long->date-time-string midnight-in-helsinki)))
    (is (= "2025-01-01 02:00" (time/long->date-time-string midnight-in-utc)))))

(deftest long->index-postfix-time
  (testing "returns correct timestamp in UTC time"
    (is (= "03-02-2026-at-09.58.24.312" (time/long->index-postfix-time exact-time)))
    (is (= "31-12-2024-at-20.00.00.000" (time/long->index-postfix-time ten-pm-in-helsinki)))
    (is (= "31-12-2024-at-22.00.00.000" (time/long->index-postfix-time midnight-in-helsinki)))
    (is (= "01-01-2025-at-00.00.00.000" (time/long->index-postfix-time midnight-in-utc)))))

(deftest long->date-time
  (testing "returns correct instant"
    (is (= [2026  2  3  9 58 24 312 "UTC"] (breakdown (time/long->date-time exact-time))))
    (is (= [2024 12 31 20  0  0   0 "UTC"] (breakdown (time/long->date-time ten-pm-in-helsinki))))
    (is (= [2024 12 31 22  0  0   0 "UTC"] (breakdown (time/long->date-time midnight-in-helsinki))))
    (is (= [2025  1  1  0  0  0   0 "UTC"] (breakdown (time/long->date-time midnight-in-utc))))
    (is (= [2025  1  1  2  0  0   0 "UTC"] (breakdown (time/long->date-time two-o-clock-in-utc))))))

(deftest date-is-before-now?
  (testing "calculates correctly"
    (with-redefs [time/current-local-date (constantly (LocalDate/of 2026 2 3))]
      (is (= true (time/date-is-before-now? "2026-02-02")))
      (is (= false (time/date-is-before-now? "2026-02-03")))
      (is (= false (time/date-is-before-now? "2026-02-04"))))
    (with-redefs [time/current-local-date (constantly (LocalDate/of 2025 1 1))]
      (is (= true (time/date-is-before-now? "2024-12-31")))
      (is (= false (time/date-is-before-now? "2025-01-01")))
      (is (= false (time/date-is-before-now? "2025-01-02"))))))

(deftest parse-utc-date-time
  (testing "returns correct ZonedDateTime"
    (is (= [2026 02 03 11 58 0 0 "UTC"] (breakdown (time/parse-utc-date-time "2026-02-03T11:58"))))
    (is (= [2025 01 01 00 00 0 0 "UTC"] (breakdown (time/parse-utc-date-time "2025-01-01T00:00"))))
    (is (= [2025 01 01 02 00 0 0 "UTC"] (breakdown (time/parse-utc-date-time "2025-01-01T02:00"))))
    (is (= [2025 07 01 02 00 0 0 "UTC"] (breakdown (time/parse-utc-date-time "2025-07-01T02:00"))))))

(deftest parse-date-time
  (testing "returns correct ZonedDateTime with seconds"
    (is (= [2026 02 03 11 58 24 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2026-02-03T11:58:24"))))
    (is (= [2025 01 01 00 00  0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-01-01T00:00:00"))))
    (is (= [2025 01 01 02 00  0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-01-01T02:00:00"))))
    (is (= [2025 07 01 02 00  0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-07-01T02:00:00")))))
  (testing "returns correct ZonedDateTime without seconds"
    (is (= [2026 02 03 11 58 0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2026-02-03T11:58"))))
    (is (= [2025 01 01 00 00 0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-01-01T00:00"))))
    (is (= [2025 01 01 02 00 0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-01-01T02:00"))))
    (is (= [2025 07 01 02 00 0 0 "Europe/Helsinki"] (breakdown (time/parse-date-time "2025-07-01T02:00"))))))

(deftest format-date
  (testing "returns correct timestamps with seconds"
    (is (= "3.2.2026 klo 11:58" (:fi (time/format-localized-date "2026-02-03T11:58:24"))))
    (is (= "3.2.2026 kl. 11:58" (:sv (time/format-localized-date "2026-02-03T11:58:24"))))
    (is (= "Feb. 3, 2026 at 11:58 AM UTC+2" (:en (time/format-localized-date "2026-02-03T11:58:24"))))
    (is (= "Feb. 3, 2026 at 11:58 AM UTC+2" (:en (time/format-localized-date "2026-02-03T11:58:24"))))
    (is (= "Jul. 14, 2026 at 03:58 PM UTC+3" (:en (time/format-localized-date "2026-07-14T15:58:24")))))
  (testing "returns correct timestamps without seconds"
    (is (= "3.2.2026 klo 11:58" (:fi (time/format-localized-date "2026-02-03T11:58"))))
    (is (= "3.2.2026 kl. 11:58" (:sv (time/format-localized-date "2026-02-03T11:58"))))
    (is (= "Feb. 3, 2026 at 11:58 AM UTC+2" (:en (time/format-localized-date "2026-02-03T11:58"))))
    (is (= "Feb. 3, 2026 at 11:58 AM UTC+2" (:en (time/format-localized-date "2026-02-03T11:58"))))
    (is (= "Jul. 14, 2026 at 03:58 PM UTC+3" (:en (time/format-localized-date "2026-07-14T15:58"))))))

(deftest year
  (testing "returns correct year"
    (is (= 2024 (time/year (time/long->date-time midnight-in-helsinki))))
    (is (= 2025 (time/year (time/long->date-time midnight-in-utc))))))

(deftest month
  (testing "returns correct month"
    (is (= 12 (time/month (time/long->date-time midnight-in-helsinki))))
    (is (= 1 (time/month (time/long->date-time midnight-in-utc))))))

(deftest start-of-year
  (testing "returns the correct date-time"
    (is (= [2025 1 1 0 0 0 0 "Europe/Helsinki"] (breakdown (time/start-of-year 2025))))
    (is (= [2024 1 1 0 0 0 0 "Europe/Helsinki"] (breakdown (time/start-of-year 2024))))))

(deftest  before?
  (testing "returns "
    (is (= true (time/before? "2024-12-31T23:59" (ZonedDateTime/parse "2025-01-01T00:00:00+02:00" ))))
    (is (= false (time/before? "2024-12-31T23:59" (ZonedDateTime/parse "2024-12-31T23:59+02:00" ))))
    (is (= false (time/before? "2024-12-31T23:59" (ZonedDateTime/parse "2024-12-31T23:58+02:00" ))))))

(deftest kevat-date?
  (testing "returns correct results for Helsinki times"
    (is (= true (time/kevat-date? (ZonedDateTime/parse "2025-01-01T00:00:00+02:00"))))
    (is (= false (time/kevat-date? (ZonedDateTime/parse "2025-12-31T23:59:59+02:00"))))
    (is (= true (time/kevat-date? (ZonedDateTime/parse "2025-07-31T23:59:59+02:00"))))
    (is (= false (time/kevat-date? (ZonedDateTime/parse "2025-08-01T00:00:00+02:00")))))
  (testing "returns correct results for UTC times"
    (is (= true (time/kevat-date? (ZonedDateTime/parse "2025-01-01T00:00:00Z"))))
    (is (= false (time/kevat-date? (ZonedDateTime/parse "2025-12-31T23:59:59Z"))))
    (is (= true (time/kevat-date? (ZonedDateTime/parse "2025-07-31T23:59:59Z"))))
    (is (= false (time/kevat-date? (ZonedDateTime/parse "2025-08-01T00:00:00Z"))))))
