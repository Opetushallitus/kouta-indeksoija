(ns kouta-indeksoija-service.util.conf-test
  (:require [clojure.test :refer :all]))

(defn- sorted-keys
      [map]
      (sort (keys map)))

(deftest conf-test
  (let [template-conf (read-string (slurp "./oph-configuration/config.edn.template"))]

    (testing "Test that ci configuration is correct"
          (let [ci-conf-template  (read-string (slurp "./ci_resources/config.edn.template"))
                ci-conf           (read-string (slurp "./ci_resources/config.edn"))]
            (is (= (sorted-keys template-conf)    (sorted-keys ci-conf-template)))
            (is (= (sorted-keys ci-conf-template) (sorted-keys ci-conf)))))

    (testing "Test that test configuration is correct"
      (let [test-conf-template  (read-string (slurp "./test_resources/config.edn.template"))]
        (is (= (sorted-keys template-conf) (sorted-keys test-conf-template)))
        (when-let [test-conf (-> (try (read-string (slurp "./test_resources/config.edn")) (catch Exception e nil))
                                 (dissoc :localstack-aws-access-key-id
                                         :localstack-aws-secret-access-key))]
          (is (= (sorted-keys test-conf-template) (sorted-keys test-conf))))))))
