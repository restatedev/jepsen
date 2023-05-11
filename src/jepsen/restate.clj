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
  (connect [this conn-spec] this)
  (disconnect! [this] this)
  (execute! [this ctx action]
    (assoc action
                         :out   nil
                         :err   nil
                         ; There's also a .getExitErrorMessage that might be
                         ; interesting here?
                         :exit  0))

  (upload! [this ctx local-paths remote-path _opts] this)
  (download! [this ctx remote-paths local-path _opts] this))

(defn noop-remote [] (NoopRemote.))

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {
                 :ssh {:dummy?                   true
                             :username                  nil
                             :password                  nil
                             :strict-host-key-checking  nil
                             :private-key-path          nil
                       }
          :concurrency  100
          :name            "restate"
          :remote          (noop-remote)
          :net              jepsen.net/noop
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
          }))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

