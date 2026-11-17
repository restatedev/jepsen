; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.os-test
  (:require [clojure.test :refer [deftest is]]
            [jepsen.control :as control]
            [jepsen.core :as core]
            [jepsen.db :as db]
            [jepsen.os :as os]
            [jepsen.os.debian :as debian]
            [restate.jepsen :as restate]))

(deftest prepare-every-worker-before-database-setup-test
  (let [nodes ["n1" "n2" "n3"]
        prepared (atom #{})
        started (atom #{})
        noop-os (reify os/OS
                  (setup! [_ _ _])
                  (teardown! [_ _ _]))
        noop-db (reify db/DB
                  (setup! [_ _ _])
                  (teardown! [_ _ _]))]
    (with-redefs [debian/os noop-os
                  control/exec (fn [& command]
                                 (when (= :apt (first command))
                                   (when (= "n3" control/*host*) (Thread/sleep 200))
                                   (swap! prepared conj control/*host*)))
                  restate/app-server (constantly noop-db)
                  restate/restate (fn [_]
                                    (reify db/DB
                                      (setup! [_ _ node]
                                        (when-not (= (set nodes) @prepared)
                                          (throw (ex-info "Database setup raced worker preparation"
                                                          {:node node :prepared @prepared})))
                                        (swap! started conj node))
                                      (teardown! [_ _ _])))]
      (let [test (assoc (restate/restate-test
                        {:nodes nodes :ssh {} :concurrency 3 :rate 10 :time-limit 1
                         :dedicated-service-nodes 0 :num-partitions 1
                         :workload "set-vo" :nemesis "none"})
                       :sessions (zipmap nodes (repeat ::session)))]
        (core/with-os test
          (control/on-nodes test (partial db/setup! (:db test))))
        (is (= (set nodes) @started))))))
