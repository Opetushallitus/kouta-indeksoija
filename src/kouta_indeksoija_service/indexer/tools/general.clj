(ns kouta-indeksoija-service.indexer.tools.general)

(defn julkaistu?
  [entry]
  (and (not (nil? entry)) (= (:tila entry) "julkaistu")))

(defn asiasana->lng-value-map
  [asiasanat]
  (map (fn [a] { (keyword (:kieli a)) (:arvo a)} ) asiasanat))