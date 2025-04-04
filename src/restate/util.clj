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
    [util :as util]]
   [jepsen-patched.util :refer [await-fn]]
   [slingshot.slingshot :refer [throw+]]))

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
        (or (= (s/trim status) "running")
            (throw+ {:type :restate-container-not-running})))
      (merge {:log-message (str "Waiting for container " container-name " to be running...")}
             opts)))))

(defn get-metadata-service-member-count []
  (-> (restatectl :meta :status :| :grep "Member .*\\[.\\+\\]" :| :wc :-l)
      Integer/parseInt))

(defn get-logs-count []
  (-> (restatectl :meta :get :-k "bifrost_config" :| :jq ".logs | length")
      Integer/parseInt))

(defn get-partition-processors-count [regex]
  (-> (restatectl :partitions :list :| :grep regex :| :wc :-l)
      Integer/parseInt))

(defn get-partition-processor-leader-count []
  (get-partition-processors-count "Leader.*Active"))

(defn get-partition-processor-follower-count []
  (get-partition-processors-count "Follower.*Active"))

(defn wait-for-partition-leaders [expected-count]
  (await-fn
   (fn [] (or (= (get-partition-processor-leader-count) expected-count)
              (throw+ {:type :restate-pp-not-ready})))
   {:status-fn (fn [_] (info "Waiting for" expected-count "leader partition processors:\n"
                             (restatectl :partitions :list :|| :true)))
    :log-interval 5000}))

(defn get-deployments-count []
  (-> (restate :deployments :list :| :grep "Active" :| :wc :-l)
      Integer/parseInt))

(defn wait-for-partition-followers [expected-count]
  (await-fn
   (fn [] (or (= (get-partition-processor-follower-count) expected-count)
              (throw+ {:type :restate-pp-not-ready})))
   {:status-fn (fn [_] (info "Waiting for" expected-count "follower partition processors:\n"
                             (restatectl :partitions :list :|| :true)))
    :log-interval 5000}))

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
   (await-fn (fn [] (c/exec :curl :--fail :--silent :--show-error :--location url))
             (merge {:log-message (str "Waiting for " url " to report healthy")
                     :log-interval 5000
                     :timeout (* 60 1000)}
                    opts))))

(defn await-service-deployment []
  (util/await-fn
   (fn [] (or (>= (get-deployments-count) 1)
              (throw+ {:type :service-deployments-not-ready}))))

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
