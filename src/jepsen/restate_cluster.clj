(ns jepsen.restate-cluster
  (:require
    [clojure.tools.logging :refer :all]
    [jepsen.control :as c]))

(defn make-single-cluster
  "Restate for a particular version."
  [tag]
  (reify jepsen.db/DB
    (setup! [_ test node]
      (let [restate-img (str "ghcr.io/restatedev/restate:" tag)
            service-img (str "")
            ]
        (if (= node (-> test :nodes first))
          (do
            ;;; n1 will have restate
            (info node "installing restate" tag)
            (c/su
              (c/exec :systemctl :restart :docker)
              (c/exec :docker :pull restate-img)
              (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" restate-img)))
          (do
            ;;; n2 ... will be running services
            (info node "installing services")
            (c/su
              ;   (c/exec :systemctl :restart :docker)
              ;   (c/exec :docker :pull service-img)
              ;   (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" service-img)A
              )))))

    (teardown! [_ _ node]
      (info node "tearing down")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true))
      )))
