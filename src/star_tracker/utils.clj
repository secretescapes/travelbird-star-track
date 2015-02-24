(ns star-tracker.utils
  (:require [clojure.java.io :as io]))


(defn select-values
  "clojure.core contains select-keys 
  but not select-values."
  [m ks]
  (reduce 
    #(if-let [v (m %2)] 
        (conj %1 v) %1) 
    [] ks))


(defn body-as-string
  "In a given request context (or hash-map contains the body key), 
  returns the body if string, else tries to read input 
  string using Java.io and slurp"
  [ctx]
  (if-let [body (:body ctx)]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))