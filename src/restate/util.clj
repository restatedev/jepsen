(ns restate.util
  (:require
   [clojure.tools.logging :refer [info]]
   [jepsen
    [control :as c]
    [util :as util]]))

(defn restate [cmd & args]
  (c/exec :docker :exec :restate :restate cmd args))

(defn restatectl [cmd & args]
  (c/exec :docker :exec :restate :restatectl cmd args))

(defn get-metadata-service-member-count []
  (info "Metadata config:" (restatectl :meta :status))
  (-> (restatectl :meta :status :| :grep "Member .*\\[.\\+\\]" :| :wc :-l)
      Integer/parseInt))

(defn wait-for-metadata-servers [expected-count]
  (util/await-fn
   (fn [] (when (= (get-metadata-service-member-count) expected-count) true))))

(defn get-logs-count []
  (-> (restatectl :meta :get :-k "bifrost_config" :| :jq ".logs | length")
      Integer/parseInt))

(defn wait-for-logs [expected-count]
  (util/await-fn
   (fn [] (when (= (get-logs-count) expected-count) true))))

(defn get-partition-processors-count [status]
  (info "Partitons config:" (restatectl :partition :status))
  (-> (restatectl :meta :status :| :grep (str " " status " ") :| :wc :-l)
      Integer/parseInt))

(defn wait-for-partition-leaders [expected-count]
  (util/await-fn
   (fn [] (when (= (get-partition-processors-count "Active") expected-count) true))))

(defn wait-for-partition-followers [expected-count]
  (util/await-fn
   (fn [] (when (= (get-partition-processors-count "Follower") expected-count) true))))

(defn get-deployments-count []
  (-> (restate :deployments :list :| :grep "host.docker.internal:9080" :| :wc :-l)
      Integer/parseInt))

(defn wait-for-deployment []
  (util/await-fn
   (fn [] (when (= (get-deployments-count) 1) true))))
