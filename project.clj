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
  :managed-dependencies [[org.clojure/clojure "1.11.4"]
                         [metosin/compojure-api "1.1.14" :exclusions [cheshire
                                                                      com.fasterxml.jackson.core/jackson-core
                                                                      com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                                                                      com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                                      ring/ring-codec
                                                                      org.clojure/core.cache
                                                                      org.clojure/core.memoize]]
                         [com.fasterxml.jackson.core/jackson-annotations "2.18.3"]
                         [cheshire "5.13.0"]
                         [clojurewerkz/quartzite "2.2.0"]
                         [clj-http "3.13.0" :exclusions [org.apache.httpcomponents/httpclient]]
                         [mount "0.1.21"]
                         [environ "1.2.0"]
                         [org.clojure/core.memoize "1.1.266"]
                         [base64-clj "0.1.1"]
                         [org.clojure/algo.generic "0.1.3"]
                         [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]
                         [cprop "0.1.20"]
                         [oph/clj-elasticsearch "0.5.4-SNAPSHOT" :exclusions [com.amazonaws/aws-java-sdk-s3]]
                         [clj-soup/clojure-soup "0.1.3"]
                         [oph/clj-log "0.3.2-SNAPSHOT"]
                         [org.clojure/tools.logging "1.3.0"]
                         [org.apache.logging.log4j/log4j-slf4j2-impl "2.24.3"]
                         [org.apache.logging.log4j/log4j-api "2.24.3"]
                         [org.apache.logging.log4j/log4j-core "2.24.3"]
                         [clj-log4j2 "0.4.0"]
                         [ring-cors "0.1.13"]
                         [software.amazon.awssdk/sqs "2.31.11" :exclusions [software.amazon.awssdk/netty-nio-client
                                                                            software.amazon.awssdk/apache-client]]
                         [software.amazon.awssdk/sso "2.31.11"]
                         [software.amazon.awssdk/ssooidc "2.31.11"]
                         [software.amazon.awssdk/apache-client "2.31.11" :exclusions [commons-logging]]
                         [org.slf4j/slf4j-api "2.0.17"]
                         [org.slf4j/jcl-over-slf4j "2.0.17"]

                         ; transitive deps
                         [clj-time "0.15.2"] ;Versioristiriita: [oph/clj-log "0.3.2-SNAPSHOT"] vs [metosin/compojure-api "1.1.14"] vs [clojurewerkz/quartzite "2.2.0"]
                         [com.fasterxml.jackson.core/jackson-databind "2.18.3"]
                         [commons-fileupload/commons-fileupload "1.6.0"]
                         [org.apache.commons/commons-compress "1.22"]
                         [org.jsoup/jsoup "1.19.1"]
                         [clj-commons/clj-yaml "1.0.29"]
                         [io.netty/netty-codec-http2 "4.1.124.Final"]
                         [com.typesafe.akka/akka-http_2.12 "10.1.15"]]
  :dependencies [[org.clojure/clojure]
                 [metosin/compojure-api]
                 [com.fasterxml.jackson.core/jackson-annotations]
                 [clojurewerkz/quartzite]
                 [cheshire]
                 [clj-http]
                 [mount]
                 [environ]
                 [org.clojure/core.memoize]
                 [base64-clj]
                 [org.clojure/algo.generic]
                 ;Configuration
                 [fi.vm.sade.java-utils/java-properties]
                 [cprop]
                 ;Elasticsearch
                 [oph/clj-elasticsearch]
                 ;Cas
                 [clj-soup/clojure-soup]
                 ;;Logging
                 [oph/clj-log]
                 [org.clojure/tools.logging]
                 [org.apache.logging.log4j/log4j-slf4j2-impl]
                 [org.apache.logging.log4j/log4j-api]
                 [org.apache.logging.log4j/log4j-core]
                 [clj-log4j2]
                 [ring-cors]
                 ;;SQS Handling
                 [software.amazon.awssdk/sqs]
                 [software.amazon.awssdk/sso]
                 [software.amazon.awssdk/ssooidc]
                 [software.amazon.awssdk/apache-client]
                 [org.slf4j/slf4j-api]
                 [org.slf4j/jcl-over-slf4j]]
  :ring {:handler kouta-indeksoija-service.api/app
         :init kouta-indeksoija-service.api/init
         :destroy kouta-indeksoija-service.api/stop
         :browser-uri "kouta-indeksoija/swagger"}
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [ring/ring-mock "0.4.0"]
                                  [org.clojure/tools.namespace "1.5.0"]
                                  [criterium "0.4.6"]
                                  [pjstadig/humane-test-output "0.11.0"]]
                   :plugins [[lein-ring "0.12.6"]
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
             :test {:env {:test "true"}
                    :dependencies [[net.java.dev.jna/jna "5.17.0"]
                                   [oph/clj-test-utils "0.5.7-SNAPSHOT" :exclusions [com.amazonaws/aws-java-sdk-s3]]
                                   [lambdaisland/kaocha "1.91.1392"]]
                    :resource-paths ["test_resources"]
                    :jvm-opts ["-Daws.accessKeyId=randomKeyIdForLocalstack"
                               "-Daws.secretKey=randomKeyForLocalstack"]
                    :injections [(require '[clj-test-utils.elasticsearch-docker-utils :as utils])
                                 (utils/global-docker-elastic-fixture)]
                    :plugins [[lein-auto "0.1.3"]
                              [lein-environ "1.2.0"]
                              [lein-test-report "0.2.0"]]}
             :ci-test {:env {:test "true"}
                       :dependencies [[ring/ring-mock "0.4.0"]
                                      [net.java.dev.jna/jna "5.12.1"]
                                      [oph/clj-test-utils "0.5.7-SNAPSHOT" :exclusions [com.amazonaws/aws-java-sdk-s3]]
                                      [lambdaisland/kaocha "1.87.1366"]]
                       :jvm-opts ["-Dlog4j.configurationFile=dev_resources/log4j2.properties"
                                  "-Dconf=ci_resources/config.edn"
                                  "-Daws.accessKeyId=randomKeyIdForLocalstack"
                                  "-Daws.secretKey=randomKeyForLocalstack"]
                       :injections [(require '[clj-test-utils.elasticsearch-docker-utils :as utils])
                                    (utils/global-docker-elastic-fixture)]
                       :plugins [[lein-auto "0.1.3"]
                                 [lein-environ "1.2.0"]
                                 [lein-test-report "0.2.0"]]}
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
