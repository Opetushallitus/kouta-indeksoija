(ns kouta-indeksoija-service.indexer.amosaa-indexer-test
  (:require [clojure.test :refer :all]
            [kouta-indeksoija-service.indexer.amosaa.toteutussuunnitelma :as toteutussuunnitelma]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.rest.eperuste]))

(use-fixtures :once (fn [tests]
                      (admin/initialize-amosaa-indices-for-reindexing)
                      (tests)))

(def opetussuunnitelma
  {:id 123 :nimi {:fi "Toteutussuunnitelma fi" :sv "Toteutussuunnitelma sv"} :tila "julkaistu"})

(def paikalliset-tutkinnonosat
  [{:id 456 :nimi {:fi "Paikallinen osa fi" :sv "Paikallinen osa sv"}
    :tosa {:omatutkinnonosa {:ammattitaidonosoittamistavat {:fi "Osoittamistavat fi" :sv "Osoittamistavat sv"}
                             :ammattitaitovaatimukset {:kohde {:fi "Kohde fi" :sv "Kohde sv"}
                                                       :vaatimukset [{:koodi "v1" :vaatimus {:fi "Vaatimus fi" :sv "Vaatimus sv"}}]}
                             :ammattitaitovaatimuksetlista nil
                             :laajuus 15.0}}}
   {:id 789 :nimi {:fi "Toinen paikallinen osa fi" :sv "Toinen paikallinen osa sv"}
    :tosa {:omatutkinnonosa {:ammattitaidonosoittamistavat {:fi "Toinen osoittamistapa fi" :sv "Toinen osoittamistapa sv"}
                             :ammattitaitovaatimukset nil
                             :ammattitaitovaatimuksetlista {:kohde {:fi "Lista kohde fi" :sv "Lista kohde sv"}
                                                            :vaatimukset []}}}}])

(deftest toteutussuunnitelma-index-test
  (testing "do index toteutussuunnitelma with embedded paikalliset tutkinnon osat"
    (with-redefs [kouta-indeksoija-service.rest.eperuste/get-opetussuunnitelma-with-cache
                  (fn [_] opetussuunnitelma)
                  kouta-indeksoija-service.rest.eperuste/get-paikalliset-tutkinnonosat-with-cache
                  (fn [_] paikalliset-tutkinnonosat)]
      (toteutussuunnitelma/do-index ["123"] (. System (currentTimeMillis)))
      (let [indexed (toteutussuunnitelma/get-from-index "123")]
        (is (some? indexed))
        (is (= "toteutussuunnitelma" (:tyyppi indexed)))
        (is (= "123" (:oid indexed)))
        (is (= "Toteutussuunnitelma fi" (get-in indexed [:nimi :fi])))
        (is (= "julkaistu" (:tila indexed)))
        (is (= 2 (count (:paikallisetTutkinnonOsat indexed))))
        (is (= "Paikallinen osa fi" (get-in indexed [:paikallisetTutkinnonOsat 0 :nimi :fi])))
        (is (= "Toinen paikallinen osa fi" (get-in indexed [:paikallisetTutkinnonOsat 1 :nimi :fi])))))))

(deftest toteutussuunnitelma-returns-nil-when-amosaa-unavailable
  (testing "do index handles nil response from AMOSAA gracefully"
    (with-redefs [kouta-indeksoija-service.rest.eperuste/get-opetussuunnitelma-with-cache
                  (fn [_] nil)
                  kouta-indeksoija-service.rest.eperuste/get-paikalliset-tutkinnonosat-with-cache
                  (fn [_] nil)]
      (is (= [] (toteutussuunnitelma/do-index ["999"] (. System (currentTimeMillis))))))))
