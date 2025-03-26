(ns kouta-indeksoija-service.util.conf
  (:require [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [environ.core :as e]
            [mount.core :refer [defstate start]]
            [clojurewerkz.quartzite.scheduler :as qs]))

(defonce env (load-config :merge [e/env (source/from-system-props) (source/from-env)]))