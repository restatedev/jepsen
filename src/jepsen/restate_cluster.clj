(ns jepsen.restate-cluster
  (:require
    [clojure.tools.logging :refer :all]
    [jepsen.control :as c]
    [jepsen.db]
    ))


(defn make-single-cluster
  "Restate for a particular version."
  [{:keys [restate-image service-image]}]
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
              (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" restate-image)
              (info node "restate has started")
              ;;
              ;; setup envoy as a side-car load balancer
              ;;
              (info node "starting envoy")
              (c/upload-resource! "envoy.yaml" "/envoy.yaml") ;;; TODO: template the nodes into envoy configuration
              (info (c/exec :docker :run :-itd :--rm
                            :-p "8000:8000"
                            :-v "/envoy.yaml:/config/envoy.yaml:ro"
                            "envoyproxy/envoy:v1.20.0"
                            :envoy "--config-path" "/config/envoy.yaml"))
              (info node "Envoy started")
              ))
          (do
            ;;; n2 ... will be running services
            (info node "installing services " service-image)
            (c/su
              (c/exec :systemctl :restart :docker)
              (c/exec :docker :pull service-image)
              (c/exec :docker :run :-itd :--rm :-p "8000:8000" service-image)
              ))))

    (teardown! [_ _ node]
      (info node "tearing down")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true)
        (c/exec :rm :-f "/envoy.yaml")
        ))))
