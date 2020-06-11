(ns kouta-indeksoija-service.lokalisointi.util
  (:require [clojure.string :refer [split]]))

(defn- deep-merge-localisation
  [a b]
  (when (not (map? a))
    (throw (RuntimeException. (str "Unable to index localisation due to conflicting keys. Cannot merge " b " to " a ))))

  (merge-with (fn [x y]
                (cond (map? y) (deep-merge-localisation x y)
                      (vector? y) (concat x y)
                      :else y))
              a b))

(defn- split-key
  [key]
  (split (name key) #"\."))

(defn localisation->nested-json
  [localisation]
  (->> (for [{key :key value :value} localisation]
         (assoc-in {} (split-key key) value))
       (reduce deep-merge-localisation)))

(defn key-value-pairs->nested-json
  [key-value-pairs]
  (->> (for [e key-value-pairs]
         (assoc-in {} (split-key (key e)) (val e)))
       (reduce deep-merge-localisation)))

(defn- dot-join
  [prefix key]
  (->> [prefix (name key)]
       (remove clojure.string/blank?)
       (clojure.string/join ".")))

(defn- flatten-json
  [prefix json]
  (->> (for [[k v] json
             :let [the-key (dot-join prefix k)]]
         (if (map? v)
           (flatten-json the-key v)
           {the-key v}))
       (flatten)
       (apply merge {})
       (into (sorted-map))))

(defn nested-json->key-value-pairs
  [json]
  (flatten-json "" json))