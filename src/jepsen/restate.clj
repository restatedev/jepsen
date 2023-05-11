(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            (jepsen [checker :as checker]
                    [cli :as cli]
                    [generator :as gen]
                    [tests :as tests])
            [jepsen.checker.timeline :as timeline]
            [jepsen.control.core]
            [jepsen.restate-client :as client]
            [jepsen.restate-cluster :as cluster]
            [jepsen.independent :as independent]
            [knossos.model :as model]
            )
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:gen-class)
  )

(defrecord NoopRemote []
  jepsen.control.core/Remote
  (connect [this conn-spec])
  (disconnect! [this])
  (execute! [this ctx action]
    (assoc action
                         :out   nil
                         :err   nil
                         ; There's also a .getExitErrorMessage that might be
                         ; interesting here?
                         :exit  0))

  (upload! [this ctx local-paths remote-path _opts])
  (download! [this ctx remote-paths local-path _opts]))

(defn noop-remote [] (NoopRemote.))

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {
          :name            "restate"
          :control          (noop-remote)
          ;:db               (cluster/make-single-cluster)
          :pure-generators true
          :client          (client/make-client)

          :checker   (checker/compose
                       {:perf  (checker/perf)
                        :timeline (timeline/html)})

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

