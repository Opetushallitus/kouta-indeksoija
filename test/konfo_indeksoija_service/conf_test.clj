(ns konfo-indeksoija-service.conf-test
  (:require [midje.sweet :refer :all]
            [konfo-indeksoija-service.util.conf :refer [env]]))

(fact "Test that oph-configuration/config.edn.template and dev-resources/config.edn contain the same keys"
  (let [template-conf (read-string (slurp "./oph-configuration/config.edn.template"))
        dev-conf (read-string (slurp (if (= (:CI env) "true") "./ci/config.edn" "./dev_resources/config.edn")))]
    (keys dev-conf) => (keys template-conf)))