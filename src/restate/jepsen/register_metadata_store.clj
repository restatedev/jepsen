(ns restate.jepsen.metadata-store-register
  "A CAS register client implemented on top of the Restate Metadata Store HTTP API.
  Note: restate-server must be compiled with the metadata-api feature."
  (:require
   [jepsen
    [checker :as checker]
    [client :as client]
    [generator :as gen]
    [independent :as independent]]
   [knossos.model :as model]
   [hato.client :as hc]
   [cheshire.core :as json]
   [slingshot.slingshot :refer [try+]]
   [restate [http :as hu]]
   [restate.jepsen.common :refer [parse-long-nil]]
   [restate.jepsen.register-ops :refer [r w cas]]))

(defrecord
 RegisterMetadatsStoreClient [] client/Client

 (setup! [_this _test])

 (open! [this test node] (assoc this
                                :node (str "n" (inc (.indexOf (:nodes test) node)))
                                :admin-api (str "http://" node ":9070")
                                :defaults (hu/defaults hu/client)
                                :random (new java.util.Random)))

 (invoke! [this _test op]
   (let [[k v] (:value op)]
     (try+
      (case (:f op)
        :read (try+ (let [value (->> (hc/get (str (:admin-api this) "/metadata/" k)
                                             (:defaults this))
                                     (:body)
                                     (parse-long-nil))]
                      (assoc op :type :ok :value (independent/tuple k value) :node (:node this)))
                    (catch [:status 404] {}
                      (assoc op :type :ok :value (independent/tuple k nil) :node (:node this))))
        :write (do
                 (hc/put (str (:admin-api this) "/metadata/" k)
                         (merge (:defaults this)
                                {:body (json/generate-string v)
                                 :headers {"if-match" "*"
                                           "etag" (str (bit-shift-left (abs (.nextInt (:random this))) 1))}}))
                 (assoc op :type :ok :node (:node this)))

        :cas (let [[expected-value new-value] v
                   [stored-value stored-version]
                   (try+
                    (let [res (hc/get (str (:admin-api this) "/metadata/" k)
                                      (:defaults this))]
                      [(parse-long-nil (:body res)) (parse-long-nil (get-in res [:headers "etag"]))])
                    ;; this key is unset
                    (catch [:status 404] {} [nil nil]))]

               (if (= stored-value expected-value)
                 (do (hc/put (str (:admin-api this) "/metadata/" k)
                             (merge (:defaults this)
                                    {:body (json/generate-string new-value)
                                     :headers {"if-match" stored-version
                                               "etag" (str (inc stored-version))}}))
                     (assoc op :type :ok :node (:node this)))
                 (assoc op :type :fail :error :precondition-failed :node (:node this)))))

      (catch [:status 412] {} (assoc op :type :fail :error :precondition-failed :node (:node this)))

      ;; the MDS API returns 404 for unset keys
      (catch [:status 404] {} (assoc op :type :ok :value nil :node (:node this)))

      (catch java.net.ConnectException {} (assoc op :type :info :error :connect :node (:node this)))

      ;; NB: :type :info events indicate that the effect on the system is uncertain
      (catch [:status 500] {} (assoc op :type :info :error :server-internal :node (:node this)))
      (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
      (catch Object {} (assoc op :type :info :error :unhandled-exception :node (:node this))))))

 (teardown! [_this _test])

 (close! [_this _test]))

(defn workload
  "Linearizable reads, writes, and compare-and-set operations on independent keys."
  [opts]
  {:client    (RegisterMetadatsStoreClient.)
   :checker   (independent/checker
               (checker/linearizable {:model     (model/cas-register)
                                      :algorithm :linear}))
   :generator (independent/concurrent-generator
               (:concurrency opts)
               (range)
               (fn [_k]
                 (->> (gen/mix [r w cas])
                      (gen/limit (:ops-per-key opts)))))})
