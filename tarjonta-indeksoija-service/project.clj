(defproject tarjonta-indeksoija-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.1.10"]
                 [clojurewerkz/elastisch "2.2.2"]]
  :ring {:handler tarjonta-indeksoija-service.api/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.5.0"]
                                  [ring/ring-mock "0.3.0"]
                                  [midje "1.8.3"]]
                   :plugins [[lein-ring "0.10.0"]
                             [lein-midje "3.2"]
                             [jonase/eastwood "0.2.3"]
                             [lein-kibit "0.1.3"]]}}
  :aliases {"run" ["ring" "server"]})
