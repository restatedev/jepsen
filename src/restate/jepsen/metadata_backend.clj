; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.metadata-backend
  "Positive check that a workload's metadata really lives in the external store it configures.

  A misconfigured metadata client can silently fall back to another store while every
  linearizability check still passes. Workloads that target an external store describe where
  their key must end up; after the run, the nemesis looks it up directly in that store from a
  worker node, and the checker fails the test unless the lookup succeeded."
  (:require
   [clojure.tools.logging :refer [info]]
   [jepsen
    [checker :as checker]
    [control :as c]
    [generator :as gen]
    [nemesis :as nemesis]]))

(def ^:private verify-f :verify-metadata-backend)
(def ^:private script-local-path "resources/verify-metadata.sh")
(def ^:private script-node-path "/tmp/verify-metadata.sh")

(defn location
  "A human-readable address of the expected key, e.g. s3://bucket/prefix/key."
  [{:keys [store bucket table key]}]
  (case store
    :s3 (str "s3://" bucket "/" key)
    :dynamodb (str "dynamodb://" table "/" key)))

(defn- script-args [{:keys [store bucket table key]}]
  (case store
    :s3 [:s3 bucket key]
    :dynamodb [:dynamodb table key]))

(defn- lookup! [backend]
  (c/su
   (c/upload script-local-path script-node-path)
   (apply c/exec :bash script-node-path (script-args backend))))

(defn verifying-nemesis
  "Wraps a nemesis so that it also handles the final lookup of the expected key."
  [base backend]
  (nemesis/compose
   {#{:start :stop} base
    #{verify-f}
    (reify nemesis/Nemesis
      (setup! [this _test] this)
      (invoke! [_this test op]
        (let [node (first (:nodes test))
              result (try
                       (c/on-nodes test [node] (fn [_test _node] (lookup! backend)))
                       {:found? true}
                       (catch Exception e
                         {:found? false
                          :error (or (:err (ex-data e)) (.getMessage e))}))]
          (info "Metadata backend check for" (location backend) "on" node ":" result)
          (assoc op :value (assoc result :location (location backend)))))
      (teardown! [_this _test]))}))

(def final-phase
  "Generator phase that runs the lookup once, after the workload has finished."
  (gen/nemesis {:type :info, :f verify-f}))

(defn checker
  "Valid only if the final lookup found the expected key."
  [backend]
  (reify checker/Checker
    (check [_this _test history _opts]
      (let [result (->> history
                        (filter #(and (= :nemesis (:process %))
                                      (= verify-f (:f %))
                                      (map? (:value %))))
                        last
                        :value)]
        (merge {:location (location backend)}
               (cond
                 (nil? result) {:valid? false, :error "the expected key was never looked up"}
                 (:found? result) {:valid? true}
                 :else {:valid? false, :error (:error result)}))))))
