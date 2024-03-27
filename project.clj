(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject kouta-indeksoija-service "9.4.2-SNAPSHOT"
  :description "Kouta-indeksoija"
  :repositories [["releases" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"
                              :username :env/artifactory_username
                              :password :env/artifactory_password
                              :sign-releases false
                              :snapshots false}]
                 ["snapshots" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"
                               :username :env/artifactory_username
                               :password :env/artifactory_password
                               :sign-releases false
                               :snapshots true}]]
  :managed-dependencies [[org.flatland/ordered "1.5.7"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/compojure-api "1.1.13" :exclusions [cheshire
                                                              com.fasterxml.jackson.core/jackson-core
                                                              com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                                                              com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                              ring/ring-codec
                                                              clj-time
                                                              joda-time
                                                              org.clojure/core.cache
                                                              org.clojure/core.memoize]]
                 [com.fasterxml.jackson.core/jackson-annotations "2.12.4"]
                 [clojurewerkz/quartzite "2.0.0" :exclusions [clj-time]]
                 [cheshire "5.10.0"]
                 [clj-http "2.3.0" :exclusions [org.apache.httpcomponents/httpclient]]
                 [mount "0.1.11"]
                 [environ "1.1.0"]
                 [org.clojure/java.jdbc "0.7.0-alpha1"]
                 [org.clojure/core.memoize "1.0.257"]
                 [org.postgresql/postgresql "9.4-1200-jdbc41"]
                 [base64-clj "0.1.1"]
                 [clj-time "0.14.3"]
                 [org.clojure/algo.generic "0.1.3"]
                 ;Configuration
                 [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]
                 [cprop "0.1.10"]
                 ;Elasticsearch
                 [oph/clj-elasticsearch "0.5.0-SNAPSHOT"]
                 ;Cas
                 [clj-soup/clojure-soup "0.1.3"]
                 ;;Logging
                 [oph/clj-log "0.3.2-SNAPSHOT"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.20.0"]
                 [org.apache.logging.log4j/log4j-api "2.20.0"]
                 [org.apache.logging.log4j/log4j-core "2.20.0"]
                 [clj-log4j2 "0.4.0"]
                 [ring-cors "0.1.11"]
                 ;;SQS Handling
                 [amazonica "0.3.48" :exclusions [com.amazonaws/aws-java-sdk
                                                  com.amazonaws/amazon-kinesis-client]]
                 [com.amazonaws/aws-java-sdk-core "1.11.916"]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.916"]]
  :ring {:handler kouta-indeksoija-service.api/app
         :init kouta-indeksoija-service.api/init
         :destroy kouta-indeksoija-service.api/stop
         :browser-uri "kouta-indeksoija/swagger"}
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [ring/ring-mock "0.3.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [criterium "0.4.4"]
                                  [pjstadig/humane-test-output "0.11.0"]]
                   :plugins [[lein-ring "0.12.5"]
                             [jonase/eastwood "0.3.5"]
                             [lein-zprint "1.2.0"]
                             [lein-kibit "0.1.3" :exclusions [org.clojure/clojure]]
                             [lein-environ "1.1.0"]
                             [lein-cloverage "1.1.1" :exclusions [org.clojure/clojure]]]
                   :resource-paths ["dev_resources"]
                   :env {:dev "true"}
                   :ring {:reload-paths ["src"]
                          :port 8100}
                   :jvm-opts ["-Daws.accessKeyId=randomKeyIdForLocalstack"
                              "-Daws.secretKey=randomKeyForLocalstack"]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             :test {:env {:test "true"} :dependencies [[net.java.dev.jna/jna "5.12.1"]
                                                       [oph/clj-test-utils "0.5.5-SNAPSHOT"]
                                                       [lambdaisland/kaocha "1.87.1366"]]
                    :resource-paths ["test_resources"]
                    :jvm-opts ["-Daws.accessKeyId=randomKeyIdForLocalstack"
                               "-Daws.secretKey=randomKeyForLocalstack"]
                    :injections [(require '[clj-test-utils.elasticsearch-docker-utils :as utils])
                                 (utils/global-docker-elastic-fixture)]
                    :plugins [[lein-test-report "0.2.0"]]}
             :ci-test {:env {:test "true"}
                       :dependencies [[ring/ring-mock "0.3.2"]
                                      [net.java.dev.jna/jna "5.12.1"]
                                      [oph/clj-test-utils "0.5.5-SNAPSHOT"]
                                      [lambdaisland/kaocha "1.87.1366"]]
                       :jvm-opts ["-Dlog4j.configurationFile=dev_resources/log4j2.properties"
                                  "-Dconf=ci_resources/config.edn"
                                  "-Daws.accessKeyId=randomKeyIdForLocalstack"
                                  "-Daws.secretKey=randomKeyForLocalstack"]
                       :injections [(require '[clj-test-utils.elasticsearch-docker-utils :as utils])
                                    (utils/global-docker-elastic-fixture)]
                       :plugins [[lein-test-report "0.2.0"]]}
             :uberjar {:ring {:port 8080}}
             :jar-with-test-fixture {:source-paths ["src", "test"]
                                     :jar-exclusions [#"perf|resources|mocks"]}} ;TODO: Better exclusion
  :aliases {"dev" ["with-profile" "+dev" "ring" "server"]
            "test" ["with-profile" "+test" ["run" "-m" "kouta-indeksoija-service.kaocha/run"]]
            "deploy" ["with-profile" "+jar-with-test-fixture" "deploy"]
            "install" ["with-profile" "+jar-with-test-fixture" "install"]
            "ci-test" ["with-profile" "+test" ["run" "-m" "kouta-indeksoija-service.kaocha/run"]]
            "eastwood" ["eastwood" "{:test-paths []}"]
            "cloverage" ["with-profile" "+test" "cloverage"]
            "uberjar" ["do" "clean" ["ring" "uberjar"]]
            "testjar" ["with-profile" "+jar-with-test-fixture" "jar"]
            "elasticdump:kouta-internal" ["with-profile" "+test" ["run" "-m" "mocks.kouta-internal-mocks"]]
            "elasticdump:kouta-external" ["with-profile" "+test" ["run" "-m" "mocks.kouta-external-mocks"]]
            "elasticdump:konfo-backend" ["with-profile" "+test" ["run" "-m" "mocks.konfo-backend-mocks"]]
            "elasticdump:tarjonta-pulssi" ["with-profile" "+test" ["run" "-m" "mocks.tarjonta-pulssi-mocks"]]}
  :resource-paths ["resources"]
  :jvm-opts ["-Dlog4j.configurationFile=dev_resources/log4j2.properties"]
  :zprint {:width 100 :old? false :style :community :map {:comma? false}})
