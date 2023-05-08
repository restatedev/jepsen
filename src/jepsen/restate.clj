(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            [jepsen [cli :as cli]
             [checker :as checker]
             [generator :as gen]
             [tests :as tests]]
            [knossos.model :as model]
            [jepsen.os.debian :as debian]

            [jepsen.restate-cluster :as cluster]
            [jepsen.restate-client :as client]
            )
  (:gen-class)
  )

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {:name            "restate"
          :os              debian/os
          :db              (cluster/make-cluster "v0.0.1")
          :pure-generators true
          :client          (client/make-client)
          :checker         (checker/linearizable
                             {:model     (model/cas-register)
                              :algorithm :linear})
          :generator       (->> (gen/mix jepsen.ops/all)
                                (gen/stagger 1)
                                (gen/nemesis nil)
                                (gen/time-limit 15))
          }
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

