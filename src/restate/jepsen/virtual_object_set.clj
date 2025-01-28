(ns restate.jepsen.virtual-object-set
  "A set service client backed by a Restate Virtual Object.
  Uses regular HTTP ingress and requires the Set service to be deployed."
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :refer [info]]
   [hato.client :as hc]
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]
    [util :as util]]
   [restate
    [util :as u]
    [http :as hu]]
   [restate.jepsen.set-ops :refer [r w]]
   [slingshot.slingshot :refer [try+]]))

(defrecord
 SetServiceClient [key opts] client/Client

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :ingress-url (u/ingress-url node opts)
          :defaults (hu/defaults hu/client)))

 (setup! [this _test]
   (info "Using service URL" (:ingress-url this))
   (util/await-fn (fn [] (->> (hc/get (str (:ingress-url this) "/Set/" key "/get") (:defaults this))
                              (:status)
                              (= 200))))
   (hc/post (str (:ingress-url this) "/Set/" key "/clear") (:defaults this)))

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

     ;; for Restate services, a 404 means deployment hasn't yet completed - unexpected during this phase!
    (catch [:status 404] {} (assoc op :type :fail :error :not-found :node (:node this)))

    (catch java.net.ConnectException {} (assoc op :type :fail :error :connect :node (:node this)))

    (catch [:status 500] {} (assoc op :type :info :info :server-internal :node (:node this)))
    (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
    (catch Object {} (assoc op :type :info))))

 (teardown! [_ _test])

 (close! [_ _test]))

(defn workload
  "Restate service-backed Set test workload."
  [opts]
  {:client    (SetServiceClient. "jepsen-set" opts)
   :checker   (checker/set-full {:linearizable? true})
   :generator (gen/reserve 5 (repeat (r)) (w))})
