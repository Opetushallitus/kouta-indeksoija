(ns kouta-indeksoija-service.test-tools
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer [is]]
            [clojure.walk :as walk]
            [kouta-indeksoija-service.elastic.tools :as tools]))

(defn parse
  [body]
  (try
    (cheshire/parse-string (slurp body) true)
    (catch Exception e nil)))

(defn refresh-index
  [indexname]
  (tools/refresh-index indexname))

(defn in?
  [e coll]
  (some #(= e %) coll))

(defn contains-same-elements-in-any-order?
  [expected actual]
  (and
   (not-empty actual)
   (= (count actual) (count expected))
   (every? #(in? % expected) actual)))

(defn debug-pretty
  [json]
  (println (cheshire/generate-string json {:pretty true})))

(defn primitive?
  [x]
  (or (string? x) (number? x) (boolean? x)))

(defn sort-primitive-array
  [x]
  (if (and (vector? x) (seq x) (primitive? (first x))) (sort x) x))

(defn order-primitive-arrays-for-comparison
  [json]
  (walk/postwalk sort-primitive-array json))

(defn order-object-arrays-for-comparison
  [json sort-by-key-path]
  (sort-by (apply comp sort-by-key-path) json))

(defn compare-json
  ([expected actual]
   (let [ordered-expected (order-primitive-arrays-for-comparison expected)
         ordered-actual (order-primitive-arrays-for-comparison actual)]
     (is (= ordered-expected ordered-actual))))
  ([expected actual object-array-sort-path sort-by-key-path]
   (let [ordered-expected (order-primitive-arrays-for-comparison expected)
         ordered-actual (order-primitive-arrays-for-comparison actual)
         ordered-search-terms (order-object-arrays-for-comparison
                               (get-in ordered-actual object-array-sort-path)
                               sort-by-key-path)]
     (is (= ordered-expected
            (assoc-in ordered-actual object-array-sort-path ordered-search-terms))))))
