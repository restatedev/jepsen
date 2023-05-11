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


(def time-limit-seconds (* 1 10))
(def number-of-keys 10)

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {
          :concurrency  100
          :name            "restate"
          :remote          (noop-remote)
          :net              jepsen.net/noop
          :pure-generators true
          :client          (client/make-client)
          :checker  (checker/perf)
          :generator  (->> (independent/concurrent-generator
                                               number-of-keys
                                               (rest (range)) ; use key values greater than 0
                                               (fn [_]
                                                 (->> (gen/mix jepsen.ops/all)
                                                     ; (gen/stagger 1/50)
                                                      (gen/limit 100))))
                                             (gen/time-limit time-limit-seconds))
          }))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

