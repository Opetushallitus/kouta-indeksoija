(ns kouta-indeksoija-service.elastic.analysis-test
  "Regressiotestit hakuindeksien analysaattoreille.

   Nämä testit ajetaan samaa Docker-imagea vasten kuin tuotanto käyttää
   (elasticsearch-kouta), joten ne kattavat myös yhdyssanojen pilkkojan
   sanalistat ja raudikko-liitännäisen.

   Testit on jaettu kahteen: mitä analyysin PITÄÄ tuottaa (recall) ja mitä sen
   EI SAA tuottaa (precision). Jälkimmäinen on tässä se tärkeämpi puoli:
   tavutuspohjainen yhdyssanojen pilkkoja tuottaa herkästi merkityksettömiä
   sanapaloja, jotka näkyvät käyttäjälle vääriä osumina. Kun lisäät sanan
   pilkkojan sanalistalle tai muutat sen asetuksia, lisää tänne testi."
  (:require [clojure.test :refer :all]
            [clj-elasticsearch.elastic-utils :refer [elastic-url]]
            [clj-http.client :as http]
            [clojure.string :refer [starts-with?]]
            [kouta-indeksoija-service.elastic.admin :as admin]
            [kouta-indeksoija-service.fixture.kouta-indexer-fixture :as fixture]))

(use-fixtures :once (fn [t]
                      (fixture/restart-elasticsearch
                       (fn []
                         (admin/initialize-indices)
                         (t)))))

(defn- search-index
  []
  (->> (admin/list-indices-and-aliases)
       (keys)
       (map name)
       (filter #(starts-with? % "koulutus-kouta-search"))
       (first)))

(defn- tokens
  [analyzer text]
  (->> (http/post (elastic-url (search-index) "_analyze")
                  {:as :json
                   :content-type :json
                   :form-params {:analyzer analyzer :text text}})
       (:body)
       (:tokens)
       (map :token)
       (set)))

(def ^:private fi-index "finnish_lemmatizer_with_decompound")
(def ^:private fi-query "finnish_lemmatizer")
(def ^:private sv-index "swedish_hunspell_with_decompound")

;; Sanapalat, joita yhdyssanojen pilkkoja tuotti yleiskielisellä sanalistalla.
;; Kukin näistä oli tuotannossa hakukelpoinen termi: esim. "voi" palautti
;; 3 464 osumaa 7 691 koulutuksesta, koska "avoin" pilkkoutui.
(def ^:private fi-roskasanapalat
  [["avoin"              "voi"]
   ["logistiikka"        "tii"]
   ["matematiikka"       "tematiikka"]
   ["perusopinnot"       "ruso"]
   ["perusopetus"        "ruso"]
   ["teknologia"         "tekno"]
   ["teknologia"         "nolo"]
   ["varhaiskasvatus"    "hai"]
   ["kulttuuriopinnot"   "tuuri"]
   ["terveysala"         "sala"]
   ["terveysala"         "terve"]
   ["osaamisala"         "sala"]
   ["harvinaiset"        "nainen"]
   ["empiirinen"         "piiri"]
   ["tieteellinen"       "tee"]
   ["Python-ohjelmointi" "ohje"]
   ["ohjelmisto"         "ohje"]
   ;; "yliopisto" ei ole opisto, ja opistoja etsivä ei halua yliopistoja.
   ["yliopisto"          "opisto"]
   ["tohtori"            "tori"]
   ["historia"           "tori"]
   ;; "opetus" ja "opettaja" eivät sisällä morfeemia "ope".
   ["opetus"             "ope"]
   ["opettaja"           "ope"]])

(def ^:private fi-yhdyssanan-osat
  [["perusopinnot"      "opinto"]
   ["perusopinnot"      "perus"]
   ["aineopinnot"       "aine"]
   ["kulttuuriopinnot"  "kulttuuri"]
   ["lähihoitaja"       "hoitaja"]
   ["terveysala"        "terveys"]
   ["varhaiskasvatus"   "kasvatus"]
   ["hoitotyö"          "työ"]
   ["terveystiede"      "tiede"]
   ["liiketalous"       "talous"]
   ["ohjelmistokehitys" "ohjelmisto"]
   ["kirkkomusiikki"    "musiikki"]])

(def ^:private sv-roskasanapalat
  [["socialarbete"        "soc"]
   ["socialarbete"        "bete"]
   ["sjukskötare"         "tar"]
   ["barnträdgårdslärare" "rar"]
   ["företagsekonomi"     "före"]
   ["företagsekonomi"     "kon"]])

(def ^:private sv-yhdyssanan-osat
  [["socialarbete"        "arbete"]
   ["socialarbete"        "social"]
   ["sjukskötare"         "skötare"]
   ["yrkeshögskola"       "högskola"]
   ["barnträdgårdslärare" "barn"]
   ["barnträdgårdslärare" "trädgård"]
   ["byggnadsteknik"      "teknik"]
   ["hälsovård"           "vård"]])

(deftest yhdyssanojen-pilkkoja-ei-tuota-roskaa-test
  (testing "suomi: yhdyssanasta ei irrota merkityksettömiä sanapaloja"
    (doseq [[sana roska] fi-roskasanapalat]
      (is (not (contains? (tokens fi-index sana) roska))
          (str "'" sana "' ei saa tuottaa sanapalaa '" roska "'"))))

  (testing "ruotsi: yhdyssanasta ei irrota merkityksettömiä sanapaloja"
    (doseq [[sana roska] sv-roskasanapalat]
      (is (not (contains? (tokens sv-index sana) roska))
          (str "'" sana "' ei saa tuottaa sanapalaa '" roska "'")))))

(deftest yhdyssanojen-pilkkoja-irrottaa-oikeat-osat-test
  (testing "suomi: yhdyssanan osat irtoavat"
    (doseq [[sana osa] fi-yhdyssanan-osat]
      (is (contains? (tokens fi-index sana) osa)
          (str "'" sana "' pitäisi tuottaa yhdyssanan osa '" osa "'"))))

  (testing "ruotsi: yhdyssanan osat irtoavat"
    (doseq [[sana osa] sv-yhdyssanan-osat]
      (is (contains? (tokens sv-index sana) osa)
          (str "'" sana "' pitäisi tuottaa yhdyssanan osa '" osa "'")))))

(deftest lemmatisointi-test
  (testing "taivutetut muodot palautuvat samaan perusmuotoon indeksoinnissa ja kyselyssä"
    (doseq [[a b] [["opinnot" "opinto"]
                   ["kirkkomusiikin" "kirkkomusiikki"]
                   ["musiikkitieteen" "musiikkitiede"]
                   ["harvinaiset" "harvinainen"]
                   ["kielet" "kieli"]]]
      (is (contains? (tokens fi-query a) b)
          (str "'" a "' pitäisi lemmatisoitua muotoon '" b "'")))))

(deftest numerot-sailyvat-test
  (testing "tokenisoija ei pudota numeroita"
    ; Lucenen "lowercase"-tokenisoija säilyttää vain kirjaimia, jolloin
    ; "3D-tulostus" muuttui muotoon "d" + "tulostus". Siksi käytämme
    ; "standard"-tokenisoijaa ja erillistä lowercase-suodatinta.
    (is (contains? (tokens fi-index "3D-tulostus") "3d"))
    (is (contains? (tokens fi-index "Matematiikka 1A") "1a"))
    (is (not (contains? (tokens fi-index "3D-tulostus") "d")))))

(deftest tokenit-ovat-pienella-test
  (testing "raudikon palauttamat erisnimien perusmuodot pienennetään"
    ; Raudikko palauttaa erisnimien perusmuodot isolla alkukirjaimella
    ; ("Ruotsi", "Python"). Ilman lowercase-suodatinta raudikon jälkeen
    ; indeksiin jäisi kirjainkoon suhteen kahdentuneita termejä.
    (doseq [analyzer [fi-index fi-query]
            text ["Ruotsin kieli" "Python-ohjelmointi" "Autot ja alan tehtävät"]]
      (let [result (tokens analyzer text)]
        (is (= result (set (map clojure.string/lower-case result)))
            (str analyzer " / '" text "' tuotti isolla kirjaimella olevia tokeneita: " result))))))

(deftest englannin-hakukentat-eivat-ole-ngrammeja-test
  (testing "englanninkielinen teksti indeksoidaan sanoina, ei osajonoina"
    ; Aiemmin hakuindeksin en-kentät indeksoitiin ngram-analysaattorilla,
    ; jolloin haku "ring" osui sanaan "Engineering" ja "nation" sanaan
    ; "International".
    (let [result (tokens "english_lemmatizer" "Engineering")]
      (is (not (contains? result "ring")))
      (is (not (contains? result "gine")))
      (is (= 1 (count result))))))
