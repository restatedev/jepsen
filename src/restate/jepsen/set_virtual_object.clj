; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.set-virtual-object
  "A set service client backed by a Restate Virtual Object.
  Uses regular HTTP ingress and requires the Set service to be deployed."
  (:require
   [clojure.tools.logging :refer [info]]
   [cheshire.core :as json]
   [hato.client :as hc]
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]
    [util :as util]]
   [restate
    [util :as u]
    [http :as hu]]
   [restate.jepsen.set-ops :refer [r w]]
   [restate.jepsen.common :refer [with-retry]]
   [slingshot.slingshot :refer [try+]]))

(defrecord
 SetServiceClient [key opts] client/Client

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :ingress-url (u/ingress-url node opts)
          :defaults (hu/defaults hu/client)))

 (setup! [this _test]
   (info "Using service URL" (str (:ingress-url this) "/Set/"))
   (util/await-fn (fn [] (->> (with-retry
                                #(hc/get (str (:ingress-url this) "/Set/" key "/get") (:defaults this)))
                              (:status)
                              (= 200))))
   (when (:dummy? (:ssh opts))
     (with-retry
       #(hc/post (str (:ingress-url this) "/Set/" key "/clear") (:defaults this)))))

 (invoke! [this _test op]
   (try+
    (case (:f op)
      :read (assoc op :type :ok  :value
                   (->> (hc/post (str (:ingress-url this) "/Set/" key "/get")
                                 (:defaults this))
                        (:body)
                        (json/parse-string))
                   :node (:node this))

      :add (do (hc/post (str (:ingress-url this) "/Set/" key "/add")
                        (merge (:defaults this)
                               {:body (json/generate-string (:value op))}))
               (assoc op :type :ok :node (:node this))))

     ;; for Restate services, a 404 means deployment hasn't yet completed - unexpected during this phase!
    (catch [:status 404] {} (assoc op :type :fail :error :not-found :node (:node this)))

    (catch java.net.ConnectException {} (assoc op :type :fail :error :connect :node (:node this)))

    (catch [:status 500] {} (assoc op :type :info :error :server-internal :node (:node this)))
    (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
    (catch Object {} (assoc op :type :info))))

 (teardown! [_ _test])

 (close! [_ _test]))

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

(defn all-nodes-ok-after-final-heal []
  (reify checker/Checker
    (check [_this _test history _opts]
      (let [nodes-with-errors (check-nodes history 10)]
        (if (seq nodes-with-errors)
          {:valid? false
           :errors (map (fn [node]
                          {:node node
                           :events (->> (last-n-events-per-node
                                         (events-after history (latest-nemesis-stop history)) 3)
                                        (filter #(= (first %) node))
                                        first
                                        second)})
                        nodes-with-errors)}
          {:valid? true})))))

(defn workload
  "Restate service-backed Set test workload."
  [opts]
  {:client    (SetServiceClient. "jepsen-set" opts)
   :checker   (checker/compose {:set (checker/set-full {:linearizable? true})
                                :heal (all-nodes-ok-after-final-heal)})
   :generator (gen/reserve 5 (repeat (r)) (w))})
