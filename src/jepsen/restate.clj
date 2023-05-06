(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [tests :as tests]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]))


(def dir     "/opt/restate")
(def binary "restate")
(def logfile (str dir "/restate.log"))
(def pidfile (str dir "/restate.pid"))

 
(defn db
  "Restate for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing restate" version)
      (c/su
        (c/exec :systemctl :restart :docker)
        (c/exec :docker :pull "ghcr.io/restatedev/restate:latest")
        (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" "ghcr.io/restatedev/restate:latest"))
      )

    (teardown! [_ test node]
      (info node "tearing down restate")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true))
      )
    ))
           

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {:name "restate"
          :os   debian/os
          :db   (db "v3.1.5")
          :pure-generators true}
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

