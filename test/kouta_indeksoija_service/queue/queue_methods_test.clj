(ns kouta-indeksoija-service.queue.queue-methods-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [kouta-indeksoija-service.queue.queue :refer :all]))

(deftest queue-unit-tests

  (testing "Collect-first should"

    (testing "return first matching value of mapping"
      (let [seq [1 2 3 4 5 6]]
        (is (= 9 (collect-first #(* 3 %) #(= 9 %) seq)))
        (is (= 8 (collect-first #(+ 2 %) #(= 8 %) seq)))))

    (testing "execute 'f' only until it finds first match"
      (let [seq    [1 2 3 4 5 6]
            f      (fn [a] (if (> a 3) (throw (Exception. "Execution went too far")) (* 3 a)))
            check? #(= 9 %)]
        (is (= 9 (collect-first f check? seq))))))

  (testing "Combine-messages should"

    (testing "combine categorized oids from messages into one map"
      (is (= {:koulutukset ["k.123.1" "k.234.1" "k.456.1" "k.123.2" "k.234.2"]
              :toteutukset ["t.123.1" "t.234.1" "t.456.1" "t.123.2" "t.234.2" "t.456.2" "t.567.2"]
              :haut ["h.123.1" "h.234.1" "h.456.1" "h.123.2" "h.234.2"]
              :hakukohteet ["hk.123.3"]}
             (combine-messages [{:koulutukset ["k.123.1" "k.234.1" "k.456.1"] :toteutukset ["t.123.1" "t.234.1" "t.456.1"] :haut ["h.123.1" "h.234.1" "h.456.1"]}
                                {:koulutukset ["k.123.2" "k.234.2"] :toteutukset ["t.123.2" "t.234.2" "t.456.2" "t.567.2"] :haut ["h.123.2" "h.234.2"]}
                                {:hakukohteet ["hk.123.3"]}]))))

    (testing "remove duplicates"
      (is (= {:koulutukset ["k.123.1" "k.234.1" "k.456.1"] :toteutukset ["t.123.1" "t.234.1" "t.456.1"] :haut ["h.123.1" "h.234.1" "h.456.1"]}
             (combine-messages
              [{:koulutukset ["k.123.1" "k.234.1" "k.456.1"] :toteutukset ["t.123.1" "t.234.1" "t.456.1"] :haut ["h.123.1" "h.234.1" "h.456.1"]}
               {:koulutukset ["k.123.1" "k.234.1" "k.456.1"] :toteutukset ["t.123.1" "t.234.1" "t.456.1"] :haut ["h.123.1" "h.234.1" "h.456.1"]}])))))

  (testing "receive should"

    (defn- mock-receive [queue] {:messages (seq (reverse queue))})

    (defn- mock-receive2 [queue] {:messages (seq (str/upper-case queue))})

    (testing "should have :queue and :messages keys in response"
      (is (= (set [:messages :queue]) (set (keys (receive {:q "queue" :f mock-receive}))))))

    (testing "call parameter ':f' with value ':q' and return response in ':messages'"
      (is (= (:messages (mock-receive "queue"))   (:messages (receive {:q "queue" :f mock-receive}))))
      (is (= (:messages (mock-receive2 "foobar")) (:messages (receive {:q "foobar" :f mock-receive2})))))

    (testing "return input ':q' parameter as ':queue'"
      (is (= "queue"  (:queue (receive {:q "queue" :f mock-receive}))))
      (is (= "foobar" (:queue (receive {:q "foobar" :f mock-receive}))))))

  (testing "body-json->map should"

    (defn- sqs-message
      [body]
      {:body body})

    (testing "return json string from message body as map"
      (let [content {:oid "123.123.123" :foobar "bar"}]
        (is (= content (body-json->map (sqs-message (json/generate-string content)))))))

    (testing "fail on non-json body"
      (is (thrown? com.fasterxml.jackson.core.JsonParseException
                   (body-json->map (sqs-message "non-json-stuff")))))))