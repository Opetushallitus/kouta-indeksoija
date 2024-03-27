(ns kouta-indeksoija-service.indexer.tools.organisaatio
  (:require [clojure.set :refer [intersection]]))

(defonce invalid-organisaatiotyypit #{"organisaatiotyyppi_05",
                                      "organisaatiotyyppi_06",
                                      "organisaatiotyyppi_07",
                                      "organisaatiotyyppi_08"})

(defonce organisaatiotyyppi-koulutustoimija            "organisaatiotyyppi_01")
(defonce organisaatiotyyppi-oppilaitos                 "organisaatiotyyppi_02")
(defonce organisaatiotyyppi-toimipiste                 "organisaatiotyyppi_03")
(defonce organisaatiotyyppi-oppisopimustoimipiste      "organisaatiotyyppi_04")

(defn- recursive-hierarkia-v4-search
  [pred level]
  (when (not (empty? level))
    (or (first (filter pred level))
        (recursive-hierarkia-v4-search pred (mapcat :children level)))))

(defn contains-organisaatiotyyppi?
  [organisaatio organisaatiotyyppi]
  (not (empty? (filter #(= organisaatiotyyppi %) (or (:organisaatiotyypit organisaatio)
                                                     (:organisaatiotyyppiUris organisaatio))))))

(defn oppilaitos?
  [organisaatio]
  (or (contains-organisaatiotyyppi? organisaatio organisaatiotyyppi-oppilaitos)
      (contains-organisaatiotyyppi? organisaatio organisaatiotyyppi-oppisopimustoimipiste)))

(defn koulutustoimija?
  [organisaatio]
  (contains-organisaatiotyyppi? organisaatio organisaatiotyyppi-koulutustoimija))

(defn toimipiste?
  [organisaatio]
  (contains-organisaatiotyyppi? organisaatio organisaatiotyyppi-toimipiste))

(defn aktiivinen?
  [organisaatio]
  (= "AKTIIVINEN" (:status organisaatio)))

(defn valid-oppilaitostyyppi?
  [organisaatio]
  (let [organisaatiotyypit (set (if (seq (:organisaatiotyypit organisaatio))
                                  (:organisaatiotyypit organisaatio)
                                  (:organisaatiotyyppiUris organisaatio)))]
    (empty? (intersection invalid-organisaatiotyypit organisaatiotyypit))))

(defn indexable?
  [organisaatio]
  (valid-oppilaitostyyppi? organisaatio))

(defn indexable-oppilaitos?
  [organisaatio]
  (and (aktiivinen? organisaatio) (or (oppilaitos? organisaatio)
                                      (toimipiste? organisaatio))))

(defn get-indexable-children
  [organisaatio]
  (filter indexable? (:children organisaatio)))

(defn find-oppilaitos-from-hierarkia
  [hierarkia]
  (recursive-hierarkia-v4-search oppilaitos? [hierarkia]))

(defn find-oppilaitos-from-cache-by-own-or-child-oid
  [cache-atom oid]
  (when-let [member (get @cache-atom oid)]
    (let [get-hierarchy-item (fn [oid] (get @cache-atom oid))
          assoc-toimipisteet (fn [oppilaitos] (-> oppilaitos
                                                  (assoc :children (vec (map get-hierarchy-item (:childOids oppilaitos))))
                                                  (dissoc :childOids)))]
    (cond
      (oppilaitos? member)(assoc-toimipisteet member)
      (toimipiste? member)(assoc-toimipisteet (get-hierarchy-item (:parentOid member)))))))

(defn attach-parent-to-oppilaitos-from-cache
  [cache-atom oppilaitos]
  (let [parent-oid (:parentOid oppilaitos)]
    (if-let [parent (get @cache-atom parent-oid)]
      (-> parent
          (assoc :children [oppilaitos])
          (dissoc :childOids))
      oppilaitos)))

(defn find-oppilaitos-hierarkia-from-cache
  [cache-atom own-or-child-oid]
  (when-let [oppilaitos (find-oppilaitos-from-cache-by-own-or-child-oid cache-atom own-or-child-oid)]
    (attach-parent-to-oppilaitos-from-cache cache-atom oppilaitos)))

(defn resolve-organisaatio-oids-to-index
  [cache-atom oids]
  (let [do-resolve (fn [oid] (when-let [item (get @cache-atom oid)]
                               (if (koulutustoimija? item)
                                 (:childOids item)
                                 (if (toimipiste? item)
                                   [(:parentOid item) oid]
                                   [oid]))))]
    (distinct (vec (remove nil? (mapcat do-resolve oids))))))

(defn find-from-organisaatio-and-children
  [organisaatio oid]
  (recursive-hierarkia-v4-search #(= (:oid %) oid) (vector organisaatio)))

(defn find-from-hierarkia
  [hierarkia oid]
  (recursive-hierarkia-v4-search #(= (:oid %) oid) [hierarkia]))

(defn filter-indexable-for-hierarkia
  [hierarkia oids]
  (->> oids
       (map #(find-from-hierarkia hierarkia %))
       (remove nil?)
       (filter indexable?)
       (vec)))

(defn filter-indexable-oids-for-hierarkia
  [hierarkia oids]
  (vec (map :oid (filter-indexable-for-hierarkia hierarkia oids))))

(defn- find-hierarkia-recursive
  [this oid]
  (if (= oid (:oid this))
    this
    (if-let [child (first (remove nil? (map #(find-hierarkia-recursive % oid) (:children this))))]
      (assoc this :children [child])
      nil)))

(defn find-hierarkia
  [everything oid]
  {:organisaatiot [(first (remove nil? (map #(find-hierarkia-recursive % oid) (:organisaatiot everything))))]})
