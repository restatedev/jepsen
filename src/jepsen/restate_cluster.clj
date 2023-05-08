(ns jepsen.restate-cluster
  (:require
    [clojure.tools.logging :refer :all]
    [jepsen.control :as c]))

(defn make-cluster
  "Restate for a particular version."
  [version]
  (reify jepsen.db/DB
    (setup! [_ _ node]
      (info node "installing restate" version)
      (c/su
        (c/exec :systemctl :restart :docker)
        (c/exec :docker :pull "ghcr.io/restatedev/restate:latest")
        (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" "ghcr.io/restatedev/restate:latest"))
      )

    (teardown! [_ _ node]
      (info node "tearing down restate")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true))
      )))

