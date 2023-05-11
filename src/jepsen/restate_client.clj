(ns jepsen.restate-client
  (:require [clojure.tools.logging :refer :all]
            [jepsen.client :as client]
            [jepsen.http-client :as http]
            [jepsen.ops :as ops]
            )
  (:use [slingshot.slingshot :only [throw+ try+]])
  )

(defrecord RestateClient [conn]
  client/Client
  (open! [this test node]
      (info node "hi")
      (assoc this :conn (http/make)))

  (setup! [this _]
    this)

  (invoke! [this _ op]
    (let [client (:conn this)
          baseurl (str "http://localhost:9090/org.example.JepsenService")
          url (str baseurl (ops/op->grpc-method op))
          request (ops/op->request op)]
      (try
        (let [{:keys [success body status]} (http/post client url request)]
          (if (not success)
            (assoc op :type :fail :error status)
            (ops/op->handle-ok op body)))
        (catch Throwable _
          (assoc op :type :fail :error :io)))))

  (teardown! [this _]
    this)

  (close! [_ _]
    ; If our connection were stateful, we'd close it here.
    ))

(defn make-client []
  (RestateClient. nil))


