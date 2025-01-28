(ns restate.jepsen.metadata-store-set
  "A set client backed by the Restate Metadata Store HTTP API.
  Note: restate-server must be compiled with the metadata-api feature."
  (:require
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]]
   [hato.client :as hc]
   [cheshire.core :as json]
   [slingshot.slingshot :refer [try+]]
   [restate [http :as hu]]
   [restate.jepsen.set-ops :refer [r w]]))

(defrecord
 SetMetadatsStoreClient [key] client/Client

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :endpoint (str "http://" node ":9070/metadata/")
          :defaults (hu/defaults hu/client)
          :random (new java.util.Random)))

 (setup! [this _test]
   (hc/put (str (:endpoint this) key)
           (merge (:defaults this)
                  {:body (json/generate-string #{})
                   :headers {"if-match" "*" "etag" "1"}})))

 (invoke! [this _test op]
   (try+
    (case (:f op)
      :read (assoc op
                   :type :ok,
                   :value (->> (hc/get (str (:endpoint this) key)
                                       (:defaults this))
                               (:body)
                               (json/parse-string)
                               set))

      :add
      (let [[new-set stored-version]
            (let [res (hc/get (str (:endpoint this) key)
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
    (catch Object {} (assoc op :type :info :error :unhandled-exception :node (:node this)))))

 (teardown! [_ _test])

 (close! [_ _test]))

(defn workload
  "Restate Metadata Store-backed Set test workload."
  [_opts]
  {:client    (SetMetadatsStoreClient. "jepsen-set")
   :checker   (checker/set-full {:linearizable? true})
   :generator (gen/reserve 5 (repeat (r)) (w))})
