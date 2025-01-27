(ns restate.jepsen.virtual-object-register
  "A CAS register service client implemented as a Restate Virtual Object.
  Uses regular HTTP ingress and requires the Register service to be deployed."
  (:require
   [jepsen [client :as client]
    [checker :as checker]
    [independent :as independent]
    [generator :as gen]]
   [knossos.model :as model]
   [hato.client :as hc]
   [cheshire.core :as json]
   [slingshot.slingshot :refer [try+]]
   [restate [http :as hu]]
   [restate.jepsen.common :refer [parse-long-nil]]
   [restate.jepsen.register-ops :refer [r w cas]]))

(defrecord
 RegisterServiceClient [] client/Client

 (setup! [_this _test])

 (open! [this test node] (assoc this
                                :node (str "n" (inc (.indexOf (:nodes test) node)))
                                :ingress-url (str "http://" node ":8080")
                                :defaults (hu/defaults hu/client)))

 (invoke! [this _test op]
   (let [[k v] (:value op)]
     (try+
      (case (:f op)
        :read (let [value
                    (->> (hc/get (str (:ingress-url this) "/Register/" k "/get")
                                 (:defaults this))
                         (:body)
                         (parse-long-nil))]
                (assoc op :type :ok :value (independent/tuple k value) :node (:node this)))

        :write (do (hc/post (str (:ingress-url this) "/Register/" k "/set")
                            (merge (:defaults this)
                                   {:body (json/generate-string v)
                                    :content-type :json}))
                   (assoc op :type :ok :node (:node this)))

        :cas (let [[old new] v]
               (hc/post (str (:ingress-url this) "/Register/" k "/cas")
                        (merge (:defaults this)
                               {:body (json/generate-string {:expected old :newValue new})
                                :content-type :json}))
               (assoc op :type :ok :node (:node this))))

      (catch [:status 412] {} (assoc op :type :fail :error :precondition-failed :node (:node this)))

      ;; for a Restate service, a 404 means deployment hasn't yet completed -
      ;; this is likely replication latency
      (catch [:status 404] {} (assoc op :type :fail :error :not-found :node (:node this)))

      (catch java.net.ConnectException {} (assoc op :type :fail :error :connect :node (:node this)))

      (catch [:status 500] {} (assoc op :type :info :info :server-internal :node (:node this)))
      (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
      (catch Object {} (assoc op :type :info)))))

 (teardown! [_this _test])

 (close! [_this _test]))

(defn workload
  "Restate service-backed Register test workload."
  [opts]
  {:client    (RegisterServiceClient.)
   :checker   (independent/checker
               (checker/linearizable {:model     (model/cas-register)
                                      :algorithm :linear}))
   :generator (independent/concurrent-generator
               (:concurrency opts)
               (range)
               (fn [_k]
                 (->> (gen/mix [r w cas])
                      (gen/limit (:ops-per-key opts)))))})
