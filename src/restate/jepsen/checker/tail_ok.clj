; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.checker.tail-ok
  "A set service client backed by a Restate Virtual Object.
  Uses regular HTTP ingress and requires the Set service to be deployed."
  (:require
   [jepsen.checker :as checker]))

(defn latest-nemesis-stop [history]
  (->> history
       (filter #(and (= :nemesis (:process %))
                     (= :stop (:f %))))
       last))

(defn events-after [history event]
  (if (nil? event)
    history
    (drop-while #(not= % event) history)))

(defn last-n-events-per-node [history n]
  (->> history
       (group-by :node)
       (map (fn [[node events]]
              [node (take-last n events)]))))

(defn contains-error? [events]
  (some #(and (= :info (:type %))
              (contains? % :error))
        events))

(defn check-nodes [history n]
  (let [nemesis-stop (latest-nemesis-stop history)
        events-after-stop (events-after history nemesis-stop)]
    (->> (last-n-events-per-node events-after-stop n)
         (filter (fn [[_node events]]
                   (contains-error? events)))
         (map first) ; Get the node from each [node events] pair
         (filter some?)
         (into #{}))))

(defn all-nodes-ok-after-final-heal
  "A liveness checker that fails if the test doesn't end with at least some number of
  :ok events on each node after the last nemesis cycle has ended."
  []
  (reify checker/Checker
    (check [_this _test history _opts]
      ;; Check the tail of event history after the final nemesis stop event; we expect
      ;; the cluster to have healed and to end on some successful (:ok) responses per node.
      (let [tail-responses-per-node 5
            nodes-with-errors (check-nodes history tail-responses-per-node)]
        (if (seq nodes-with-errors)
          {:valid? false
           :description (str "The last " tail-responses-per-node " events from some node(s) contained errors")
           :errors (map (fn [node]
                          {:node node
                           :last-events (->> (last-n-events-per-node
                                              (events-after history (latest-nemesis-stop history)) tail-responses-per-node)
                                             (filter #(= (first %) node))
                                             first
                                             second)})
                        nodes-with-errors)}
          {:valid? true})))))
