; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.checker.tail-ok
  "Liveness checker verifying that every node ends the test with successful operations after the final nemesis heal."
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

(defn- indeterminate-error? [event]
  (and (= :info (:type event))
       (contains? event :error)))

;; A healed node can still see an occasional error, such as a write the backend throttled
;; through all its retries. Only repeated errors among a node's last events count as the
;; node not having recovered.
(def ^:private max-tail-errors 1)

(defn- unrecovered? [events]
  (> (count (filter indeterminate-error? events)) max-tail-errors))

(defn check-nodes [history n]
  (let [nemesis-stop (latest-nemesis-stop history)
        events-after-stop (events-after history nemesis-stop)]
    (->> (last-n-events-per-node events-after-stop n)
         (filter (fn [[_node events]]
                   (unrecovered? events)))
         (map first) ; Get the node from each [node events] pair
         (filter some?)
         (into #{}))))

(defn all-nodes-ok-after-final-heal
  "A liveness checker that fails if any node's last events after the final nemesis cycle
  contain more than one indeterminate error."
  []
  (reify checker/Checker
    (check [_this _test history _opts]
      ;; Check the tail of event history after the final nemesis stop event; we expect
      ;; the cluster to have healed and to end on some successful (:ok) responses per node.
      (let [tail-responses-per-node 5
            nodes-with-errors (check-nodes history tail-responses-per-node)]
        (if (seq nodes-with-errors)
          {:valid? false
           :description (str "The last " tail-responses-per-node " events from some node(s) contained more than " max-tail-errors " error")
           :errors (map (fn [node]
                          {:node node
                           :last-events (->> (last-n-events-per-node
                                              (events-after history (latest-nemesis-stop history)) tail-responses-per-node)
                                             (filter #(= (first %) node))
                                             first
                                             second)})
                        nodes-with-errors)}
          {:valid? true})))))
