; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.set-metadata-store
  "A set client backed by the Restate Metadata Store HTTP API.
  Note: restate-server must be compiled with the metadata-api feature."
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :refer [info]]
   [hato.client :as hc]
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]]
   [restate
    [util :as u]
    [http :as hu]]
   [restate.jepsen.checker.tail-ok :refer [all-nodes-ok-after-final-heal]]
   [restate.jepsen.set-ops :refer [r w]]
   [slingshot.slingshot :refer [try+]]))

(defrecord
 SetMetadatsStoreClient [key opts] client/Client

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :endpoint (str (u/admin-url node opts) "/metadata/")
          :defaults (hu/defaults hu/client)
          :random (new java.util.Random)))

 (setup! [this _test]
   (info "Using service URL" (:endpoint this))
   (hc/put (str (:endpoint this) key)
           (merge (:defaults this)
                  {:body (json/generate-string #{})
                   :headers {"if-match" "*" "etag" "1"}})))

 (invoke! [this _test op]
   (try+
    (case (:f op)
      :read (assoc op
                   :type :ok,
                   :value (->>
                           (hc/get (str (:endpoint this) key)
                                   (:defaults this))
                           (:body)
                           (json/parse-string)
                           set)
                   :node (:node this))

      :add
      (let [[new-set stored-version]
            (let [res
                  (hc/get (str (:endpoint this) key)
                          (:defaults this))]
              [(conj (->> (json/parse-string (:body res)) set) (:value op))
               (parse-long (get-in res [:headers "etag"]))])]
        (hc/put (str (:endpoint this) key)
                (merge (:defaults this)
                       {:body (json/generate-string new-set)
                        :headers {"if-match" (str stored-version)
                                  "etag" (str (inc stored-version))}}))
        (assoc op :type :ok)))
    (catch [:status 412] {} (assoc op :type :fail :error :precondition-failed :node (:node this)))
    (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
    (catch Object {}
      (info "Exception:" (:throwable &throw-context))
      (assoc op :type :info :error :unhandled-exception :node (:node this)))))

 (teardown! [_ _test])

 (close! [_ _test]))

(defn workload
  "Restate Metadata Store-backed Set test workload."
  [opts]
  {:client    (SetMetadatsStoreClient. "jepsen-set" opts)
   :checker   (checker/compose {:set (checker/set-full {:linearizable? true})
                                :heal (all-nodes-ok-after-final-heal)})
   :generator (gen/reserve 5 (repeat (r)) (w))
   :heal-time 20})
