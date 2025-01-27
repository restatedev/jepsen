(ns restate.jepsen.virtual-object-set
  "A set service client backed by a Restate Virtual Object.
  Uses regular HTTP ingress and requires the Set service to be deployed."
  (:require
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]]
   [hato.client :as hc]
   [cheshire.core :as json]
   [slingshot.slingshot :refer [try+]]
   [restate [http :as hu]]))

(defrecord
 SetServiceClient [key] client/Client

 (setup! [this _test]
   (hc/post (str (:ingress-url this) "/Set/" key "/clear") (:defaults this)))

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :ingress-url (str "http://" node ":8080")
          :defaults (hu/defaults hu/client)))

 (invoke! [this _test op]
   (try+
    (case (:f op)
      :read (assoc op :type :ok :value
                   (->> (hc/post (str (:ingress-url this) "/Set/" key "/get")
                                 (:defaults this))
                        (:body)
                        (json/parse-string)))

      :add (do
             (hc/post (str (:ingress-url this) "/Set/" key "/add")
                      (merge (:defaults this)
                             {:body (json/generate-string (:value op))}))
             (assoc op :type :ok :node (:node this))))

     ;; for Restate services, a 404 means deployment hasn't yet completed -
     ;; this is likely replication latency
    (catch [:status 404] {} (assoc op :type :fail :error :not-found :node (:node this)))

    (catch java.net.ConnectException {} (assoc op :type :fail :error :connect :node (:node this)))

    (catch [:status 500] {} (assoc op :type :info :info :server-internal :node (:node this)))
    (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
    (catch Object {} (assoc op :type :info))))

 (teardown! [_ _test])

 (close! [_ _test]))

(defn w
  []
  (->> (range)
       (map (fn [x] {:type :invoke, :f :add, :value x}))))

(defn r
  []
  {:type :invoke, :f :read, :value nil})

(defn workload
  "Restate service-backed Set test workload."
  [_opts]
  {:client    (SetServiceClient. "jepsen-set")
   :checker   (checker/set-full {:linearizable? true})
   :generator (gen/reserve 5 (repeat (r)) (w))})
