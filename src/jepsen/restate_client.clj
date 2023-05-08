(ns jepsen.restate-client
  (:require [jepsen.client :as client]
            [jepsen.http-client :as http]
            [jepsen.ops :as ops]
            ))

(defrecord RestateClient [baseurl conn]
  client/Client
  (open! [this _ node]
    (assoc this :baseurl (str  "http://" node ":8081/dev.restate.JepsenService")
                :conn (http/make)
                ))

  (setup! [this _]
    this)

  (invoke! [this _ op]
    (let [baseurl (:baseurl this)
          client (:conn this)
          url    (str baseurl (ops/op->grpc-method op))
          request (ops/op->argument op)
          {:keys [success body status]} (http/post client url request)
          ]
      (if (not success)
        (assoc op :type :fail :error status)
        (ops/op->handle-ok op body))))


  (teardown! [this _]
    this)

  (close! [_ _]
    ; If our connection were stateful, we'd close it here.
    ))

(defn make-client []
  (RestateClient. nil nil))


