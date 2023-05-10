(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            (jepsen [checker :as checker]
                    [cli :as cli]
                    [generator :as gen]
                    [tests :as tests])
            [jepsen.checker.timeline :as timeline]
            [jepsen.os.debian :as debian]
            [jepsen.restate-client :as client]
            [jepsen.restate-cluster :as cluster]
            [jepsen.independent :as independent]
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
         {
          :name            "restate"
          :os              debian/os
          :db              (cluster/make-single-cluster)
          :pure-generators true
          :client          (client/make-client)

          :checker   (checker/compose
                       {:perf  (checker/perf)
                        :indep (independent/checker
                                 (checker/compose
                                   {:linear   (checker/linearizable {:model (model/cas-register 0)
                                                                     :algorithm :linear})
                                    :timeline (timeline/html)}))})

          :nemesis         (cluster/container-killer)
          :generator  (->> (independent/concurrent-generator
                                               10
                                               (rest (range)) ; use key values greater than 0
                                               (fn [_]
                                                 (->> (gen/mix jepsen.ops/all)
                                                      (gen/stagger 1/50)
                                                      (gen/limit 100))))
                                             (gen/nemesis
                                               (cycle [(gen/sleep 5)
                                                       {:type :info, :f :start}
                                                       (gen/sleep 5)
                                                       {:type :info, :f :stop}]))
                                             (gen/time-limit (:time-limit opts)))
          }
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

