(ns kouta-indeksoija-service.elastic.settings)

(def index-settings
  {:index.mapping.total_fields.limit 2000
   :index.max_ngram_diff "35"
   :number_of_shards "1"
   :analysis {:filter {:ngram_compound_words_and_conjugations {:type "ngram" ;automaa utomaat tomaati omaatio maatioi aatioin atioins tioinsi ioinsin oinsinö insinöö nsinöör
                                                               :min_gram "3"
                                                               :max_gram "30"
                                                               :token_chars ["letter", "digit"]}
                       :finnish_stop {:type "stop"
                                      :stopwords "_finnish_"}
                       :finnish_stemmer {:type "stemmer"
                                         :language "finnish"}
                       :finnish_stemmer_for_long_words {:type "condition"
                                                        :filter ["finnish_stemmer"]
                                                        :script {:source "token.getTerm().length() > 5"}}
                       :finnish_raudikko {:type "raudikko"}
                       ;; Sanalista on generoitu indeksoidusta datasta, ks.
                       ;; tools/generate_decompound_wordlist.py. Yleiskielistä sanalistaa
                       ;; (words.txt) EI saa käyttää: tavutuspohjainen pilkkoja poimii
                       ;; tavurajojen välistä minkä tahansa listalta löytyvän merkkijonon,
                       ;; joten iso yleislista tuottaa roskaa ("avoin" -> "voi",
                       ;; "logistiikka" -> "tii", "perusopinnot" -> "ruso").
                       ;; min_subword_size on 3, jotta lyhyet mutta olennaiset osat
                       ;; ("ala", "työ", "osa") irtoavat. Se on turvallista vain karsitulla
                       ;; listalla.
                       :finnish_decompound {:type "hyphenation_decompounder"
                                            :hyphenation_patterns_path "decompound/fi/hyphenation.xml"
                                            :word_list_path "decompound/fi/words-lemmat.txt"
                                            :min_word_size "5"
                                            :min_subword_size "3"
                                            :max_subword_size "100"
                                            :only_longest_match "false"}
                       :swedish_stop {:type "stop"
                                      :stopwords "_swedish_"}
                       :swedish_stemmer {:type "stemmer"
                                         :language "swedish"}
                       :swedish_stemmer_for_long_words {:type "condition"
                                                        :filter ["swedish_stemmer"]
                                                        :script {:source "token.getTerm().length() > 5"}}
                       :swedish_hunspell {:type "hunspell"
                                          :locale "sv"}
                       ;; Ks. finnish_decompound.
                       :swedish_decompound {:type "hyphenation_decompounder"
                                            :hyphenation_patterns_path "decompound/sv/hyphenation.xml"
                                            :word_list_path "decompound/sv/words-lemmat.txt"
                                            :min_word_size "5"
                                            :min_subword_size "3"
                                            :max_subword_size "100"
                                            :only_longest_match "false"}
                       :english_stop {:type "stop"
                                      :stopwords "_english_"}
                       :english_keywords {:type "keyword_marker"
                                          :keywords "_english_keywords_"}
                       :english_stemmer {:type "stemmer"
                                         :language "english"}
                       :english_stemmer_for_long_words {:type "condition"
                                                        :filter ["english_stemmer"]
                                                        :script {:source "token.getTerm().length() > 5"}}
                       :english_possessive_stemmer {:type "stemmer"
                                                    :language "possessive_english"}},
              ;; HUOM. tokenisoijasta: "lowercase" on Lucenen kirjaintokenisoija, joka
              ;; säilyttää VAIN kirjaimia — numerot katoavat kokonaan ("3D-tulostus" ->
              ;; "d" + "tulostus"). Hakuindeksien analysaattorit käyttävät siksi
              ;; "standard"-tokenisoijaa ja erillistä lowercase-suodatinta.
              ;; Virkailijapuolen ngram-analysaattorit (finnish/swedish/english) on
              ;; jätetty ennalleen, jotta niiden osajonohaku ei muutu.
              :analyzer {;; HUOM: finnish/swedish/english ovat ngram-analysaattoreita ja
                         ;; tarkoitettu vain virkailijapuolen kouta-* ja koodisto-indekseihin,
                         ;; joissa osajonohaku on tarkoituksellista. Näitä EI saa käyttää
                         ;; hakuindekseissä: ngram tekee hausta osajonohaun, jolloin esim.
                         ;; "ring" osuu sanaan "Engineering".
                         :finnish {:type "custom"
                                   :tokenizer "lowercase"
                                   :filter ["finnish_stop"
                                            "ngram_compound_words_and_conjugations"
                                            "remove_duplicates"]}
                         ;; Pintamuodot ilman lemmatisointia. Käytetään prefix-alikentissä,
                         ;; joissa autocomplete tarvitsee kesken kirjoitetun sanan alun.
                         :finnish_words {:type "custom"
                                         :tokenizer "standard"
                                         :filter ["lowercase"
                                                  "finnish_stop"
                                                  "remove_duplicates"]}
                         ;; lowercase raudikon JÄLKEEN: raudikko palauttaa erisnimien
                         ;; perusmuodot isolla alkukirjaimella ("Ruotsi", "Python"), mikä
                         ;; kahdentaisi termit indeksissä.
                         :finnish_lemmatizer {:type "custom"
                                              :tokenizer "standard"
                                              :filter ["lowercase"
                                                       "finnish_stop"
                                                       "finnish_raudikko"
                                                       "lowercase"
                                                       "remove_duplicates"]}
                         :finnish_lemmatizer_with_decompound {:type "custom"
                                              :tokenizer "standard"
                                              :filter ["lowercase"
                                                       "finnish_stop"
                                                       "finnish_raudikko"
                                                       "lowercase"
                                                       "finnish_decompound"
                                                       "remove_duplicates"]}
                         :finnish_keyword {:type "custom"
                                           :tokenizer "lowercase"
                                           :filter ["finnish_stop"
                                                    "finnish_stemmer_for_long_words"]}
                         :swedish {:type "custom"
                                   :tokenizer "lowercase"
                                   :filter ["swedish_stop"
                                            "ngram_compound_words_and_conjugations"
                                            "remove_duplicates"]}
                         :swedish_words {:type "custom"
                                         :tokenizer "standard"
                                         :filter ["lowercase"
                                                  "swedish_stop"
                                                  "remove_duplicates"]}
                         :swedish_hunspell {:type "custom"
                                            :tokenizer "standard"
                                            :filter ["lowercase"
                                                     "swedish_stop"
                                                     "swedish_hunspell"
                                                     "lowercase"
                                                     "remove_duplicates"]}
                         :swedish_hunspell_with_decompound {:type "custom"
                                                            :tokenizer "standard"
                                                            :filter ["lowercase"
                                                                     "swedish_stop"
                                                                     "swedish_hunspell"
                                                                     "lowercase"
                                                                     "swedish_decompound"
                                                                     "remove_duplicates"]}
                         :swedish_keyword {:type "custom"
                                           :tokenizer "lowercase"
                                           :filter ["swedish_stop"
                                                    "swedish_stemmer_for_long_words"]}
                         :english {:type "custom"
                                   :tokenizer "lowercase"
                                   :filter ["english_stop"
                                            "english_possessive_stemmer"
                                            "ngram_compound_words_and_conjugations"
                                            "remove_duplicates"]}
                         ;; Hakuindeksien englanti: sama analysaattori indeksoinnissa ja
                         ;; kyselyssä, ei ngrammeja. Vastaa english_keywordin käyttäytymistä
                         ;; kyselypuolella.
                         :english_lemmatizer {:type "custom"
                                              :tokenizer "standard"
                                              :filter ["lowercase"
                                                       "english_possessive_stemmer"
                                                       "english_stop"
                                                       "english_stemmer_for_long_words"
                                                       "remove_duplicates"]}
                         :english_keyword {:type "custom"
                                           :tokenizer "lowercase"
                                           :filter ["english_stop"
                                                    "english_possessive_stemmer"
                                                    "english_stemmer_for_long_words"]}
                         :english_words {:type "custom"
                                         :tokenizer "standard"
                                         :filter ["lowercase"
                                                  "english_possessive_stemmer"
                                                  "english_stop"
                                                  "remove_duplicates"]}}
              :normalizer {:case_insensitive {:filter "lowercase"}}}})

(def index-settings-search (merge index-settings {:index.max_inner_result_window 500}))

(def index-settings-eperuste (merge index-settings {:index.mapping.total_fields.limit 4000}))

(def index-settings-lokalisointi
  {:index.mapping.total_fields.limit 2000})

(def toteutussuunnitelma-mappings
  {:properties {:paikallisetTutkinnonOsat {:type "nested"
                                          :properties {:id                           {:type "keyword"}
                                                       :ammattitaidonosoittamistavat {:type "object" :dynamic true}
                                                       :ammattitaitovaatimukset      {:type "object" :dynamic true}}}}})

(def lokalisointi-mappings
  {:dynamic_templates [{:all {:match "*",
                             :match_mapping_type "string",
                             :mapping {:type "keyword",
                                       :norms false}}}]})

(def eperuste-mappings
  {:properties {:suoritustavat {:type "nested"
                                :properties {:tutkinnonOsaViitteet {:type "nested"
                                                                    :properties {:laajuus {:type "float"}}}}}}
   :dynamic_templates [{:fi {:match "kieli_fi"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "finnish"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:sv {:match "kieli_sv"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "swedish"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:en {:match "kieli_en"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "english"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}]})

(def osaamismerkki-mappings
  {:dynamic_templates [{:fi {:match "kieli_fi"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "finnish"
                                       :norms false
                                       :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:sv {:match "kieli_sv"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "swedish"
                                       :norms false
                                       :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:en {:match "kieli_en"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "english"
                                       :norms false
                                       :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}]})

(def koodisto-mappings
  {:dynamic_templates [{:nested {:match "koodit"
                                 :match_mapping_type "object"
                                 :mapping { :type "nested" }}}
                       {:fi {:match "fi"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "finnish"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256}}}}}
                       {:sv {:match "sv"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "swedish"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256}}}}}
                       {:en {:match "en"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "english"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256}}}}}]})

;; Hakuindeksien kielikohtaiset tekstikentät. Määritelty kertaalleen, jotta
;; dynamic_templates ja nimikenttien eksplisiittiset mappaukset eivät pääse
;; eriytymään toisistaan.
(def ^:private search-lng-text-mappings
  {:fi {:type "text"
        :analyzer "finnish_lemmatizer_with_decompound"
        :search_analyzer "finnish_lemmatizer"
        :norms false
        :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}
                 :words {:type "text"
                         :analyzer "finnish_lemmatizer"
                         :search_analyzer "finnish_lemmatizer"}}}
   :sv {:type "text"
        :analyzer "swedish_hunspell_with_decompound"
        :search_analyzer "swedish_hunspell"
        :norms false
        :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}
                 :words {:type "text"
                         :analyzer "swedish_hunspell"
                         :search_analyzer "swedish_hunspell"}}}
   ;; Englanti käyttää samaa analysaattoria indeksoinnissa ja kyselyssä. Aiemmin
   ;; indeksointi tehtiin ngram-analysaattorilla "english", jolloin haku muuttui
   ;; osajonohauksi: "ring" osui sanaan "Engineering" ja "nation" sanaan
   ;; "International".
   :en {:type "text"
        :analyzer "english_lemmatizer"
        :search_analyzer "english_lemmatizer"
        :norms false
        :fields {:keyword {:type "keyword" :ignore_above 256 :normalizer "case_insensitive"}
                 :words {:type "text"
                         :analyzer "english_words"
                         :search_analyzer "english_words"}}}})

(def ^:private prefix-analyzers
  {:fi "finnish_words" :sv "swedish_words" :en "english_words"})

;; Nimikentät saavat lisäksi prefix-alikentän, jota autocomplete käyttää.
;; Prefix-alikenttä indeksoi pintamuodot ilman lemmatisointia, koska kesken
;; kirjoitettu sana ("lähihoitaj") ei lemmatisoidu miksikään.
;; Prefix-alikenttä on tarkoituksella vain nimikentissä eikä kaikissa
;; kielikentissä: search_terms sisältää satoja kielikenttiä (asiasanat,
;; nimikkeet, koodistojen nimet), ja alikentän lisääminen niihin kaikkiin
;; kasvattaisi kenttämäärän lähelle index.mapping.total_fields.limit-rajaa.
(def ^:private search-nimi-mapping
  {:properties (into {} (for [lng [:fi :sv :en]]
                          [lng (assoc-in (get search-lng-text-mappings lng)
                                         [:fields :prefix]
                                         {:type "text"
                                          :analyzer (get prefix-analyzers lng)
                                          :search_analyzer (get prefix-analyzers lng)})]))})

(def kouta-search-mappings
  {:properties {:search_terms {:type "nested",
                               :properties {:koulutusnimi search-nimi-mapping
                                            :toteutusNimi search-nimi-mapping
                                            :nimi         search-nimi-mapping
                                            :hakutiedot {:type "nested"
                                                         :properties {:hakutapa {:type "keyword"}
                                                                      :yhteishakuOid {:type "keyword"}
                                                                      :pohjakoulutusvaatimukset {:type "keyword"}
                                                                      :valintatavat {:type "keyword"}
                                                                      :hakuajat {:type "nested"
                                                                                 :properties {:alkaa   {:type "date" }
                                                                                              :paattyy {:type "date" }}}}}
                                            :metadata {:properties {:opintojenLaajuusNumero {:type "float"}
                                                                    :tutkinnonOsat {:type "nested"
                                                                                    :properties {:opintojenLaajuusNumero {:type "float"}}}
                                                                    :paikallisetTutkinnonOsat {:type "nested"
                                                                                               :properties {:opintojenLaajuusNumero {:type "float"}
                                                                                                            :opetussuunnitelmaId {:type "keyword"}
                                                                                                            :tutkinnonosaId {:type "keyword"}}}}}}}}
   :dynamic_templates [{:nested {:match "search_terms"
                                 :match_mapping_type "object"
                                 :mapping { :type "nested" }}}
                       {:fi {:match "fi"
                             :match_mapping_type "string"
                             :mapping (:fi search-lng-text-mappings)}}
                       {:sv {:match "sv"
                             :match_mapping_type "string"
                             :mapping (:sv search-lng-text-mappings)}}
                       {:en {:match "en"
                             :match_mapping_type "string"
                             :mapping (:en search-lng-text-mappings)}}
                       {:tila {:match "tila"
                               :match_mapping_type "string"
                               :mapping {:type "text"
                                         :analyzer "finnish"
                                         :norms false
                                         :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}]})

(def kouta-mappings
  {:properties {:metadata {:properties {:opintojenLaajuusNumero {:type "float"}
                                        :tutkinnonOsat {:type "nested"
                                                        :properties {:opintojenLaajuusNumero {:type "float"}}}}}}
   :dynamic_templates [{:haut {:match "haut"
                               :mapping {:type "keyword"}
                               :match_mapping_type "string"}}
                       {:muokkaaja {:match "muokkaaja.nimi"
                                    :match_mapping_type "string"
                                    :mapping {:type "text"
                                              :analyzer "finnish"
                                              :norms false
                                              :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:fi {:match "fi"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "finnish"
                                       :search_analyzer "finnish_keyword"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:sv {:match "sv"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "swedish"
                                       :search_analyzer "swedish_keyword"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:en {:match "en"
                             :match_mapping_type "string"
                             :mapping {:type "text"
                                       :analyzer "english_keyword"
                                       :norms false
                                       :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:tila {:match "tila"
                               :match_mapping_type "string"
                               :mapping {:type "text"
                                         :analyzer "finnish"
                                         :norms false
                                         :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:hakutapa {:match "hakutapa.koodiUri"
                               :match_mapping_type "string"
                               :mapping {:type "text"
                                         :analyzer "finnish"
                                         :norms false
                                         :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:koulutuksenAlkamiskausi {:match "metadata.koulutuksenAlkamiskausi.koulutuksenAlkamiskausi.koodiUri"
                                   :match_mapping_type "string"
                                   :mapping {:type "text"
                                             :analyzer "finnish_keyword"
                                             :norms false
                                             :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}
                       {:koulutuksenAlkamisvuosi {:match "metadata.koulutuksenAlkamiskausi.koulutuksenAlkamisvuosi"
                                                  :match_mapping_type "string"
                                                  :mapping {:type "text"
                                                            :analyzer "finnish_keyword"
                                                            :norms false
                                                            :fields { :keyword { :type "keyword" :ignore_above 256 :normalizer "case_insensitive"}}}}}]})
