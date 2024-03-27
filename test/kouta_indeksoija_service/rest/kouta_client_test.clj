(ns kouta-indeksoija-service.rest.kouta-client-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.rest.kouta :as kouta]))

;; tyhjennetään cachet reloadilla
(defn reload-rest-kouta-fixture [f]
  (require 'kouta-indeksoija-service.rest.kouta :reload)
  (f))

(use-fixtures :once reload-rest-kouta-fixture)

(def call-count (atom 0))

(use-fixtures :each (fn [test]
                      (reset! call-count 0)
                      (test)))

(def response-body {:oid "1"})

(defn mock-get-koulutus
  [_ _]
  (swap! call-count inc)
  response-body)

(deftest kouta-client-test
  (testing "kouta client hits target endpoint only once and then returns cached results"
    (with-redefs [kouta/cas-authenticated-get-as-json mock-get-koulutus]
      (repeatedly 3
                  (is (= response-body
                         (kouta/get-doc-with-cache "koulutus" "1" 1234))))
      (is (= 1 @call-count))))

  (testing "kouta client hits target endpoint once for each unique request"
    (with-redefs [kouta/cas-authenticated-get-as-json mock-get-koulutus]
      (is (= response-body
             (kouta/get-doc-with-cache "koulutus" "1" 1234)))
      (is (= response-body
             (kouta/get-doc-with-cache "koulutus" "1" 3456)))
      (is (= response-body
             (kouta/get-doc-with-cache "koulutus" "1" 1234)))
      (is (= response-body
             (kouta/get-doc-with-cache "koulutus" "1" 7890)))
      (is (= 3 @call-count)))))
