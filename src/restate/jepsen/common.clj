; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.common
  (:require [clj-commons.slingshot :refer [try+]]))

(defn parse-long-nil
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (parse-long s)))

(defn get-env
  "Wrapper for System/getenv to enable testing"
  [var-name]
  (System/getenv var-name))

(defn aws-creds
  "Static S3-compatible credentials for workloads that cannot use the worker nodes' instance
  role, with precedence: --access-key-id/--secret-access-key CLI options, then the
  AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY environment variables. Only call this from a
  workload that needs them: the values end up in the test map, which Jepsen logs and
  persists with the test results."
  [opts]
  {:access-key-id (or (:access-key-id opts)
                      (get-env "AWS_ACCESS_KEY_ID"))
   :secret-access-key (or (:secret-access-key opts)
                          (get-env "AWS_SECRET_ACCESS_KEY"))})

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
