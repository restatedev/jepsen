(ns jepsen.restate-cluster
  (:require
    [clojure.tools.logging :refer :all]
    [jepsen.control :as c]
    [jepsen.db]
    [jepsen.docker :as docker]
    [jepsen.nemesis :as nemesis]
    ))



(def service-image "ghcr.io/restatedev/jepsen:latest")
(def restate-image "ghcr.io/restatedev/restate:latest")

(defn start-restate []
  (docker/start-container {
                           :label "restate"
                           :image restate-image
                           :ports [8081 9090]
                           }))

(defn stop-restate []
  (docker/stop-container "restate"))

(defn start-envoy []
  (docker/start-container {
                           :label "envoy"
                           :image "envoyproxy/envoy:v1.20.0"
                           :ports [8000]
                           :mount [["/envoy.yaml" "/config/envoy.yaml"]]
                           :args  [:envoy "--config-path" "/config/envoy.yaml"]
                           }
                          ))

(defn start-service-endpoint
  []
  (docker/start-container {
                           :label "service"
                           :ports [8000]
                           :image service-image
                           }))
(defn stop-service-endpoint
  []
  (docker/stop-container "service"))

(defn make-single-cluster
  "Restate for a particular version."
  []
  (reify jepsen.db/DB
    (setup! [_ test node]
      (if (= node (-> test :nodes first))
        (do
          ;;; n1 will have restate
          (info node "installing restate" restate-image)
          (c/su
            ;; not sure what's the problem with debain+vagrant, but this is needed, and it is getting too late
            ;; to figure it out.
            (c/exec :systemctl :restart :docker)
            ;;
            ;; setup restate
            ;;
            (c/exec :docker :pull restate-image)
            ;(c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" restate-image)
            (start-restate)
            (info node "restate has started")
            ;;
            ;; setup envoy as a side-car load balancer
            ;;
            (info node "starting envoy")
            (c/upload-resource! "envoy.yaml" "/envoy.yaml") ;;; TODO: template the nodes into envoy configuration
            (start-envoy)
            (info node "Envoy started")
            ))
        (do
          ;;; n2 ... will be running services
          (info node "installing services " service-image)
          (c/su
            (c/exec :systemctl :restart :docker)
            (c/exec :docker :pull service-image)
            (start-service-endpoint)
            ))))

    (teardown! [_ _ node]
      (info node "tearing down")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true) ;; basically kill all docker
        (c/exec :rm :-f "/envoy.yaml")
        ))))


(defn container-killer
  "Responds to :start by killing containers on random nodes and to :stop by healing it."
  []
  (nemesis/node-start-stopper
    (fn [_ nodes] (rand-nth nodes))
    (fn start [test node]
      (do
        (if (= node (-> test :nodes first))
          (stop-restate)
          (stop-service-endpoint))
        [:component-killed-on node]))
    (fn stop [test node]
      (do
        (if (= node (-> test :nodes first))
          (start-restate)
          (start-service-endpoint))
        )
      [:component-restarted-on node])))
