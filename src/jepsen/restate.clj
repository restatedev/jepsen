(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            (jepsen [checker :as checker]
                    [cli :as cli]
                    [generator :as gen]
                    [tests :as tests])
            [jepsen.os.debian :as debian]
            [jepsen.restate-client :as client]

            [jepsen.restate-cluster :as cluster]
            [knossos.model :as model]
            )
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class)
  )

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {:name            "restate"
          :os              debian/os
          :db              (cluster/make-single-cluster "latest")
          :pure-generators true
          :client          (client/make-client)

          :checker         (checker/compose
                             {:perf   (checker/perf)
                              :linear (checker/linearizable {:model     (model/cas-register)
                                                             :algorithm :linear})})


          :generator       (->> (gen/mix jepsen.ops/all)
                                ;  (gen/stagger 1)
                                (gen/nemesis
                                  (gen/phases (cycle [(gen/sleep 5)
                                                      {:type :info, :f :start}
                                                      (gen/sleep 5)
                                                      {:type :info, :f :stop}])))
                                (gen/time-limit (:time-limit opts)))
          }
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

