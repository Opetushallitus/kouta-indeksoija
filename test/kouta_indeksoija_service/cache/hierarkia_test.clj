(ns kouta-indeksoija-service.cache.hierarkia-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.test-tools :as tools]
            [kouta-indeksoija-service.fixture.common-indexer-fixture :refer [common-indexer-fixture]]
            [kouta-indeksoija-service.indexer.tools.organisaatio :as o]
            [kouta-indeksoija-service.indexer.cache.hierarkia :as cache]))

(use-fixtures :each common-indexer-fixture)

(defonce use-alternative-hierarkia (atom false))

(defn mock-get-all-organisaatiot []
  (if @use-alternative-hierarkia
    (do (reset! use-alternative-hierarkia false)
        (tools/parse (str "test/resources/organisaatiot/hierarkia-alt.json")))
    (do (reset! use-alternative-hierarkia true)
        (tools/parse (str "test/resources/organisaatiot/hierarkia.json")))))

(defn mock-get-by-oid
  [oid]
  (tools/parse (str "test/resources/organisaatiot/" oid ".json")))

(defn mock-find-last-changes [last-modified]
  (tools/parse (str "test/resources/organisaatiot/last-modified.json")))

(deftest hierarkia-test
  (with-redefs [kouta-indeksoija-service.rest.organisaatio/get-all-organisaatiot mock-get-all-organisaatiot
                kouta-indeksoija-service.rest.organisaatio/get-by-oid mock-get-by-oid]
    (testing "Fetching hierarkia via cache should"
      (testing "return hierarkia with correct organisation data"
        (let [res (cache/get-hierarkia-cached)]
          (is (= 92 (count (keys @res))))
          (is (= 56 (count (filter #(o/toimipiste? %) (vals @res)))))
          (is (= 29 (count (filter #(o/oppilaitos? %) (vals @res)))))
          (is (= 7 (count (filter #(o/koulutustoimija? %) (vals @res)))))))
      (testing "return correct koulutustoimijat"
        (let [res (cache/get-hierarkia-cached)
              koulutustoimijat (filter #(o/koulutustoimija? (get @res %)) (keys @res))]
          (is (= ["1.2.246.562.10.594252633210"
                  "1.2.246.562.10.11111111111"
                  "1.2.246.562.10.53814745062"
                  "1.2.246.562.10.10101010100"
                  "1.2.246.562.10.73481747332"
                  "1.2.246.562.10.2014041511401945349694"
                  "1.2.246.562.10.55867603791"]
                 koulutustoimijat))
          (is (= {:oid "1.2.246.562.10.594252633210"
                  :kotipaikkaUri "kunta_398"
                  :nimi {:fi "Koulutuskeskus Salpaus -kuntayhtymä" :sv "Koulutuskeskus Salpaus -kuntayhtymä sv"}
                  :status "AKTIIVINEN"
                  :organisaatiotyypit ["organisaatiotyyppi_01"]
                  :childOids ["1.2.246.562.10.66634895871" "1.2.246.562.10.99999999999" "1.2.246.562.10.81934895871"]}
                 (get @res "1.2.246.562.10.594252633210")))
          (is (= {:oid "1.2.246.562.10.53814745062"
                  :kotipaikkaUri "kunta_091"
                  :nimi {:en "Helsingin Yliopisto" :fi "Helsingin Yliopisto" :sv "Helsingin Yliopisto"}
                  :status "AKTIIVINEN"
                  :organisaatiotyypit ["organisaatiotyyppi_01"]
                  :childOids ["1.2.246.562.10.112212847610" "1.2.246.562.10.81927839589" "1.2.246.562.10.39218317368"]}
                 (get @res "1.2.246.562.10.53814745062")))
          (is (= {:oid "1.2.246.562.10.73481747332"
                  :kotipaikkaUri "kunta_759"
                  :nimi {:en "Soinin kunta" :fi "Soinin kunta" :sv "Soinin kunta"}
                  :status "AKTIIVINEN"
                  :organisaatiotyypit ["organisaatiotyyppi_01", "organisaatiotyyppi_07", "organisaatiotyyppi_09"]
                  :childOids ["1.2.246.562.10.53670619591"]}
                 (get @res "1.2.246.562.10.73481747332")))
          (is (= {:oid "1.2.246.562.10.2014041511401945349694"
                  :kotipaikkaUri "kunta_743"
                  :nimi {:en "Seinäjoen Ammattikorkeakoulu Oy" :fi "Seinäjoen Ammattikorkeakoulu Oy" :sv "Seinäjoen Ammattikorkeakoulu Oy"}
                  :status "AKTIIVINEN"
                  :organisaatiotyypit ["organisaatiotyyppi_01"]
                  :childOids ["1.2.246.562.10.54453921329"]}
                 (get @res "1.2.246.562.10.2014041511401945349694")))
          (is (=  {:oid "1.2.246.562.10.55867603791"
                   :kotipaikkaUri "kunta_186"
                   :nimi {:en "Keski-Uudenmaan koulutuskuntayhtymä" :fi "Keski-Uudenmaan koulutuskuntayhtymä" :sv "Keski-Uudenmaan koulutuskuntayhtymä"}
                   :status "AKTIIVINEN"
                   :organisaatiotyypit ["organisaatiotyyppi_01" "organisaatiotyyppi_07"]
                   :childOids ["1.2.246.562.10.197113642410" "1.2.246.562.10.32506551657" "1.2.246.562.10.31186964119"]}
                  (get @res "1.2.246.562.10.55867603791")))))
      (testing "return correct oppilaitokset"
        (let [res (cache/get-hierarkia-cached)
              oppilaitokset (filter #(o/oppilaitos? (get @res %)) (keys @res))]
          (is (tools/contains-same-elements-in-any-order? ["1.2.246.562.10.10101010101", "1.2.246.562.10.53670619591", "1.2.246.562.10.54545454545", "1.2.246.562.10.197113642410", "1.2.246.562.10.77777777799",
                                                           "1.2.246.562.10.112212847610", "1.2.246.562.10.67476956288", "1.2.246.562.10.54453921329", "1.2.246.562.10.81927839589", "1.2.246.562.10.39218317368",
                                                           "1.2.246.562.10.32506551657", "1.2.246.562.10.66634895871", "1.2.246.562.10.81934895871", "1.2.246.562.10.000002", "1.2.246.562.10.99999999999",
                                                           "1.2.246.562.10.00101010101", "1.2.246.562.10.00101010102", "1.2.246.562.10.00101010103", "1.2.246.562.10.00101010104", "1.2.246.562.10.00101010105",
                                                           "1.2.246.562.10.00101010106", "1.2.246.562.10.00101010107", "1.2.246.562.10.0000011", "1.2.246.562.10.0000012", "1.2.246.562.10.0000013",
                                                           "1.2.246.562.10.0000014", "1.2.246.562.10.0000015", "1.2.246.562.10.55555555555", "1.2.246.562.10.31186964119"] oppilaitokset))
          (is (= {:childOids []
                  :oid "1.2.246.562.10.53670619591"
                  :kieletUris ["oppilaitoksenopetuskieli_1#2"]
                  :kotipaikkaUri "kunta_759"
                  :nimi {:en "Soinin yhtenäiskoulu" :fi "Soinin yhtenäiskoulu" :sv "Soinin yhtenäiskoulu"}
                  :oppilaitostyyppi "oppilaitostyyppi_11#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.73481747332"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.53670619591")))
          (is (= {:childOids ["1.2.246.562.10.78314029667"]
                  :oid "1.2.246.562.10.197113642410"
                  :kieletUris ["oppilaitoksenopetuskieli_1#1"]
                  :kotipaikkaUri "kunta_858"
                  :nimi {:en "Pekka Halosen akatemia" :fi "Pekka Halosen akatemia" :sv "Pekka Halosen akatemia"}
                  :oppilaitostyyppi "oppilaitostyyppi_63#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.55867603791"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.197113642410")))
          (is (= {:childOids []
                  :oid "1.2.246.562.10.112212847610"
                  :kieletUris ["oppilaitoksenopetuskieli_1#1"]
                  :kotipaikkaUri "kunta_091"
                  :nimi {:en "Helsingin normaalilyseo" :fi "Helsingin normaalilyseo" :sv "Helsingin normaalilyseo"}
                  :oppilaitostyyppi "oppilaitostyyppi_19#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.53814745062"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.112212847610")))
          (is (= {:childOids ["1.2.246.562.10.41117090922" "1.2.246.562.10.42160341923" "1.2.246.562.10.82061758015"
                              "1.2.246.562.10.83918870482" "1.2.246.562.10.52750255714"]
                  :oid "1.2.246.562.10.54453921329"
                  :kieletUris ["oppilaitoksenopetuskieli_1#1"
                               "oppilaitoksenopetuskieli_4#2"]
                  :kotipaikkaUri "kunta_743"
                  :nimi {:en "Seinäjoki University of Applied Sciences" :fi "Seinäjoen ammattikorkeakoulu"
                         :sv "Seinäjoen ammattikorkeakoulu"}
                  :oppilaitostyyppi "oppilaitostyyppi_41#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.2014041511401945349694"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.54453921329")))
          (is (= {:childOids []
                  :oid "1.2.246.562.10.81927839589"
                  :kieletUris ["oppilaitoksenopetuskieli_1#2"]
                  :kotipaikkaUri "kunta_091"
                  :nimi {:en "Helsingin yliopiston Viikin normaalikoulu"
                         :fi "Helsingin yliopiston Viikin normaalikoulu"
                         :sv "Helsingin yliopiston Viikin normaalikoulu"}
                  :oppilaitostyyppi "oppilaitostyyppi_19#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.53814745062"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.81927839589")))
          (is (= {:childOids ["1.2.246.562.10.61397511793" "1.2.246.562.10.45501388404" "1.2.246.562.10.85024656564"
                              "1.2.246.562.10.84134527203" "1.2.246.562.10.80520096209" "1.2.246.562.10.94639300915"
                              "1.2.246.562.10.80593660139" "1.2.246.562.10.74168867104" "1.2.246.562.10.73683668492"
                              "1.2.246.562.10.73307006806" "1.2.246.562.10.27277224708" "1.2.246.562.10.41092748163"
                              "1.2.246.562.10.24134663046" "1.2.246.562.10.67451633415" "1.2.246.562.10.94997228401"
                              "1.2.246.562.10.445049088710" "1.2.246.562.10.38836199083" "1.2.246.562.10.43256000142"
                              "1.2.246.562.10.74478323608" "1.2.246.562.10.41941575486" "1.2.246.562.10.37753840224"
                              "1.2.246.562.10.39147468688"]
                  :oid "1.2.246.562.10.39218317368"
                  :kieletUris ["oppilaitoksenopetuskieli_1#1" "oppilaitoksenopetuskieli_2#1"
                               "oppilaitoksenopetuskieli_4#1"]
                  :kotipaikkaUri "kunta_091"
                  :nimi {:en "University of Helsinki" :fi "Helsingin yliopisto" :sv "Helsingfors universitet"}
                  :oppilaitostyyppi "oppilaitostyyppi_42#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.53814745062"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.39218317368")))
          (is (= {:childOids ["1.2.246.562.10.37818936132" "1.2.246.562.10.43682075486" "1.2.246.562.10.72599284709"
                              "1.2.246.562.10.80117936338" "1.2.246.562.10.33553959546" "1.2.246.562.10.29631644423"
                              "1.2.246.562.10.92613592937" "1.2.246.562.10.47353291801" "1.2.246.562.10.94410465259"
                              "1.2.246.562.10.19049298185"]
                  :oid "1.2.246.562.10.32506551657"
                  :kieletUris ["oppilaitoksenopetuskieli_1#2"]
                  :kotipaikkaUri "kunta_186"
                  :nimi {:en "Keuda" :fi "Keuda" :sv "Keuda"}
                  :oppilaitostyyppi "oppilaitostyyppi_21#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.55867603791"
                  :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.32506551657")))
          (is (= {:childOids []
                  :oid "1.2.246.562.10.67476956288"
                  :kieletUris ["oppilaitoksenopetuskieli_1#2"] :kotipaikkaUri "kunta_297"
                  :nimi {:fi "Jokin järjestyspaikka"
                         :sv "Jokin järjestyspaikka sv"}
                  :oppilaitostyyppi "oppilaitostyyppi_21#1"
                  :organisaatiotyypit ["organisaatiotyyppi_02"]
                  :parentOid "1.2.246.562.10.11111111111" :status "AKTIIVINEN"}
                 (get @res "1.2.246.562.10.67476956288")))))
      (testing "return correct toimipisteet, grandchildren toimipisteet relocated under oppilaitos"
        (let [res (cache/get-hierarkia-cached)
              toimipisteet (filter #(o/toimipiste? (get @res %)) (keys @res))]
          (is (tools/contains-same-elements-in-any-order? ["1.2.246.562.10.777777777993", "1.2.246.562.10.777777777991", "1.2.246.562.10.61397511793", "1.2.246.562.10.45501388404",
                                                           "1.2.246.562.10.37818936132", "1.2.246.562.10.85024656564", "1.2.246.562.10.43682075486", "1.2.246.562.10.84134527203",
                                                           "1.2.246.562.10.80520096209", "1.2.246.562.10.78314029667", "1.2.246.562.10.41117090922", "1.2.246.562.10.94639300915",
                                                           "1.2.246.562.10.80593660139", "1.2.246.562.10.74168867104", "1.2.246.562.10.10101010103", "1.2.246.562.10.10101010109",
                                                           "1.2.246.562.10.73683668492", "1.2.246.562.10.73307006806", "1.2.246.562.10.27277224708", "1.2.246.562.10.42160341923",
                                                           "1.2.246.562.10.41092748163", "1.2.246.562.10.24134663046", "1.2.246.562.10.72599284709", "1.2.246.562.10.54545454522",
                                                           "1.2.246.562.10.80117936338", "1.2.246.562.10.33553959546", "1.2.246.562.10.67451633415", "1.2.246.562.10.94997228401",
                                                           "1.2.246.562.10.82061758015", "1.2.246.562.10.777777777992", "1.2.246.562.10.445049088710", "1.2.246.562.10.38836199083",
                                                           "1.2.246.562.10.43256000142", "1.2.246.562.10.10101010102", "1.2.246.562.10.29631644423", "1.2.246.562.10.74478323608",
                                                           "1.2.246.562.10.54545454511", "1.2.246.562.10.41941575486", "1.2.246.562.10.92613592937", "1.2.246.562.10.47353291801",
                                                           "1.2.246.562.10.37753840224", "1.2.246.562.10.94410465259", "1.2.246.562.10.39147468688", "1.2.246.562.10.83918870482",
                                                           "1.2.246.562.10.19049298185", "1.2.246.562.10.52750255714", "1.2.246.562.10.000003", "1.2.246.562.10.000004",
                                                           "1.2.246.562.10.66603619189", "1.2.246.562.10.67603619189", "1.2.246.562.10.66634895666", "1.2.246.562.10.001010101011",
                                                           "1.2.246.562.10.001010101012", "1.2.246.562.10.001010101021", "1.2.246.562.10.001010101022", "1.2.246.562.10.001010101023"] toimipisteet))))
      (testing "Toimipiste -type of grandchildren relocated under oppilaitos"
        (let [res (cache/get-hierarkia-cached)]
          (is (= "1.2.246.562.10.39218317368" (:parentOid (get @res "1.2.246.562.10.73683668492"))))
          (is (= "1.2.246.562.10.73307006806" (:parentToimipisteOid (get @res "1.2.246.562.10.73683668492"))))
          (is (= "1.2.246.562.10.39218317368" (:parentOid (get @res "1.2.246.562.10.27277224708"))))
          (is (= "1.2.246.562.10.94997228401" (:parentToimipisteOid (get @res "1.2.246.562.10.27277224708"))))
          (is (= "1.2.246.562.10.39218317368" (:parentOid (get @res "1.2.246.562.10.24134663046"))))
          (is (= "1.2.246.562.10.37753840224" (:parentToimipisteOid (get @res "1.2.246.562.10.24134663046"))))
          (is (= "1.2.246.562.10.39218317368" (:parentOid (get @res "1.2.246.562.10.38836199083"))))
          (is (= "1.2.246.562.10.94639300915" (:parentToimipisteOid (get @res "1.2.246.562.10.38836199083")))))))

    (testing "Clearing hierarkia cache causes refresh"
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.80117936338"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.33553959546"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.29631644423"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.43682075486"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.19049298185"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.92613592937"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.47353291801"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.72599284709"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.94410465259"))))
      (is (= "AKTIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.37818936132"))))
      (cache/clear-all-cached-data)
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.80117936338"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.33553959546"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.29631644423"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.43682075486"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.19049298185"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.92613592937"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.47353291801"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.72599284709"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.94410465259"))))
      (is (= "PASSIIVINEN" (:status (cache/get-hierarkia-item "1.2.246.562.10.37818936132")))))
    (testing "return OPH data correctly"
      (is (= {:oid "1.2.246.562.10.00000000001"
              :status "AKTIIVINEN"
              :nimi {:fi "Opetushallitus"
                     :sv "Utbildningsstyrelsen",
                     :en "Finnish National Agency for Education"}}
             (cache/get-hierarkia-item "1.2.246.562.10.00000000001"))))))

(deftest yhteystieto-test
  (with-redefs [kouta-indeksoija-service.rest.organisaatio/get-by-oid mock-get-by-oid
                kouta-indeksoija-service.rest.organisaatio/find-last-changes mock-find-last-changes]
    (testing "Fetching yhteystiedot via cache should"
      (testing "return correct data"
        (let [res (cache/get-yhteystiedot "1.2.246.562.10.52750255714")]
          (is (= "AKTIIVINEN" (:status res)))
          (is (= "Seinäjoen ammattikorkeakoulu, SeAMK" (get-in res [:nimi :fi])))
          (is (= "Seinäjoen ammattikorkeakoulu, SeAMK" (get-in res [:nimi :sv])))
          (is (= "Seinäjoki University of Applied Sciences, SeAMK" (get-in res [:nimi :en])))
          (is (= 10 (count (:yhteystiedot res)))))
        (let [other (cache/get-yhteystiedot "1.2.246.562.10.54453921329")]
          (is (= "AKTIIVINEN" (:status other)))
          (is (= "Seinäjoen ammattikorkeakoulu" (get-in other [:nimi :fi])))
          (is (= "Seinäjoen ammattikorkeakoulu" (get-in other [:nimi :sv])))
          (is (= "Seinäjoki University of Applied Sciences" (get-in other [:nimi :en])))
          (is (= 5 (count (:yhteystiedot other))))))
      (testing "return same data from cache when fetching again"
        (let [res (cache/get-yhteystiedot "1.2.246.562.10.52750255714")]
          (is (= "AKTIIVINEN" (:status res)))
          (is (= "Seinäjoen ammattikorkeakoulu, SeAMK" (get-in res [:nimi :fi])))
          (is (= "Seinäjoen ammattikorkeakoulu, SeAMK" (get-in res [:nimi :sv])))
          (is (= "Seinäjoki University of Applied Sciences, SeAMK" (get-in res [:nimi :en])))
          (is (= 10 (count (:yhteystiedot res)))))))
    (testing "Fetching changes should"
      (testing "Fetch all changed organisaatiot (of any organisaatiotyyppi)"
        (let [changed (cache/get-all-muutetut-organisaatiot-cached (System/currentTimeMillis))]
          (is (= ["1.2.246.562.10.52750255714"
                  "1.2.246.562.10.54453921329"
                  "1.2.246.562.10.197113642410"
                  "1.2.246.562.10.78314029667" 
                  "1.2.246.562.10.94791481685" 
                  "1.2.246.562.10.67247086191"] 
                 changed))))
      (testing "Refresh data in cache and fetch missing yhteystiedot"
        (is (= "PASSIIVINEN" (:status (cache/get-yhteystiedot "1.2.246.562.10.52750255714")) ))
        (is (= "PASSIIVINEN" (:status (cache/get-yhteystiedot "1.2.246.562.10.54453921329")) ))
        (is (= "AKTIIVINEN" (:status (cache/get-yhteystiedot "1.2.246.562.10.197113642410")) ))
        (is (= "Pekka Halosen akatemia" (get-in (cache/get-yhteystiedot "1.2.246.562.10.197113642410") [:nimi :fi]) ))
        (is (= "AKTIIVINEN" (:status (cache/get-yhteystiedot "1.2.246.562.10.78314029667")) ))
        (is (= "Pekka Halosen akatemia" (get-in (cache/get-yhteystiedot "1.2.246.562.10.78314029667") [:nimi :fi]) )))
      (testing "Unnecessary organisaatiotyypit not cached")
      (is (nil? (cache/get-yhteystiedot "1.2.246.562.10.94791481685")))
      (is (nil? (cache/get-yhteystiedot "1.2.246.562.10.67247086191"))))))
