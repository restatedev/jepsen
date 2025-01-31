(ns restate.jepsen.common
  (:require [slingshot.slingshot :refer [try+]]))

(defn parse-long-nil
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (parse-long s)))

;; Since client concurrency is bounded, we just apply a fairly dumb policy to paper over network failures
(def max-retry-attemps 10)
(def retry-delay-millis 50)

(defn with-retry [func]
  (loop [n max-retry-attemps]
    (let [[result err]
          (try+
           [(func) nil]
           (catch [:status 500] {} [nil (:throwable &throw-context)])
           (catch java.net.http.HttpTimeoutException {} [nil (:throwable &throw-context)]))]

      (cond
        (nil? err)
        result

        (> n 0)
        (do
          (Thread/sleep retry-delay-millis)
          (recur (dec n)))

        :else
        (throw err)))))
