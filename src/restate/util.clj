; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.util
  (:require
   [clojure.string :as s]
   [clojure.tools.logging :refer [info]]
   [jepsen
    [control :as c]
    [util :as util]]))

(defn restate [cmd & args]
  (c/exec :docker :exec :restate :restate cmd args))

(defn restatectl [cmd & args]
  (c/exec :docker :exec :restate :restatectl cmd args))

(defn wait-for-container
  "Blocks until a Docker container with the given name is running.

   Options:
     :retry-interval   How long between retries, in ms. Default 1s.
     :log-interval     How long between logging that we're still waiting, in ms.
                       Default matches retry-interval.
     :timeout          How long until giving up and throwing :type :timeout, in ms.
                       Default 60 seconds."
  ([container-name]
   (wait-for-container container-name {}))
  ([container-name opts]
   (info "Waiting for container" container-name "to be running...")
   (util/await-fn
    (fn []
      (let [status (c/exec :docker :inspect :--format "{{.State.Status}}" container-name)]
        (= (s/trim status) "running"))
      (merge {:log-message (str "Waiting for container " container-name " to be running...")}
             opts)))))

(defn get-metadata-service-member-count []
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

(defn get-partition-processors-count [regex]
  (-> (restatectl :partitions :list :| :grep regex :| :wc :-l)
      Integer/parseInt))

(defn wait-for-partition-leaders [expected-count]
  (util/await-fn
   (fn [] (= (get-partition-processors-count "Leader.*Active") expected-count))
   {:log-message (str "Waiting for " expected-count " leader partition processors...")}))

(defn wait-for-partition-followers [expected-count]
  (util/await-fn
   (fn [] (= (get-partition-processors-count "Follower.*Active") expected-count))
   {:log-message (str "Waiting for " expected-count " follower partition processors...")}))

(defn get-deployments-count []
  (-> (restate :deployments :list :| :grep "host.docker.internal:9080" :| :wc :-l)
      Integer/parseInt))

(defn await-tcp-port
  ;; copy of the built-in Jepsen one with ability to set custom host
  "Blocks until a local TCP port is bound. Options:

  :retry-interval   How long between retries, in ms. Default 1s.
  :log-interval     How long between logging that we're still waiting, in ms.
                    Default `retry-interval.
  :timeout          How long until giving up and throwing :type :timeout, in
                    ms. Default 60 seconds."
  ([host port]
   (await-tcp-port host port {}))
  ([host port opts]
   (util/await-fn
    (fn check-port []
      (c/exec :nc :-z host port)
      nil)
    (merge {:log-message (str "Waiting for port " port " ...")} opts))))

(defn await-url
  ([url]
   (await-url url {}))
  ([url opts]
   (util/await-fn (fn [] (c/exec :curl :--fail :--silent :--show-error :--location url))
                  (merge {:log-message (str "Waiting for " url "...")
                          :log-interval 5000
                          :timeout (* 120 1000)}
                         opts))))

(defn await-service-deployment []
  (util/await-fn
   (fn [] (>= (get-deployments-count) 2))) ;; expecting at least Set + Register

  (info "Waiting for service to become callable...")
  (await-url "http://localhost:8080/Set/0/get"))

(defn restate-server-node-count [opts] (- (count (:nodes opts)) (:dedicated-service-nodes opts)))

(defn restate-server-nodes [opts]
  (take (restate-server-node-count opts) (:nodes opts)))

(defn restate-server-node?
  "Determines whether a given node is a restate-server node. If the cluster is
  heterogeneous, the first (N - dedicated-service-nodes) are reserved for
  running restate-server only, with the rest only hosting the application
  service components. Given a node index, this function returns true if the
  given node should run restate-server."
  [node opts] (< (.indexOf (:nodes opts) node)
                 (restate-server-node-count opts)))

(defn app-server-node?
  "Determines whether a given node is an SDK service node. If the cluster is
  hereogeneous, the last dedicated-service-nodes nodes are reserved for running
  application service components only. Given a node index, this function returns
  whether the given node should run services."
  [node opts] (or (= 0 (:dedicated-service-nodes opts)) (not (restate-server-node? node opts))))

(defn admin-url
  "Given a node id, determine the appropriate admin URL to call. In homogeneous
  clusters, the URL is based on the node address. In heterogeneous clusters,
  app-server nodes get a statically-assigned random restate-server node to call."
  [node opts]
  (if (restate-server-node? node opts)
    (str "http://" node ":9070")
    (str "http://" (rand-nth (restate-server-nodes opts)) ":9070")))

(defn ingress-url
  "Given a node id, determine the appropriate ingress URL to call. In homogeneous
  clusters, the URL is based on the node address. In heterogeneous clusters,
  app-server nodes get a statically-assigned random restate-server node to call."
  [node opts]
  (if (restate-server-node? node opts)
    (str "http://" node ":8080")
    (str "http://" (rand-nth (restate-server-nodes opts)) ":8080")))
