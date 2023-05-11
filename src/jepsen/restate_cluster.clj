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

(def restate-volume "restate")

(defn start-restate []
  (docker/start-container {
                           :label "restate"
                           :image restate-image
                           :ports [8081 9090]
                           :mount [[restate-volume "/target:rw"]]
                           }))

(defn stop-restate []
  (docker/stop-container "restate"))

(defn start-envoy []
  (docker/start-container {
                           :label "envoy"
                           :image "envoyproxy/envoy:v1.20.0"
                           :ports [8000]
                           :mount [["/envoy.yaml" "/config/envoy.yaml:ro"]]
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


