(ns jepsen.restate-client
  (:require [clojure.tools.logging :refer :all]
            [jepsen.client :as client]
            [jepsen.http-client :as http]
            [jepsen.ops :as ops]
            )
  (:use [slingshot.slingshot :only [throw+ try+]])
  )

(defrecord RestateClient [baseurl conn am-i-restate? restate-node]
  client/Client
  (open! [this test node]
    (let [restate-node (-> test :nodes first)
          baseurl (str "http://" restate-node ":9090/org.example.JepsenService")
          ]
      (info "New client talking to " restate-node)
      (assoc this :baseurl baseurl
                  :conn (http/make)
                  :am-i-restate? (= node restate-node)
                  :restate-node restate-node
                  )))

  (setup! [this _]
    (when (:am-i-restate? this)
      ;;; we need to do discovery
      (info "Starting auto discovery")
      (info "Discovery"
            (http/post (:conn this)
                       (str "http://" (:restate-node this) ":8081/endpoint/discover") ; <-- meta
                       {:uri (str "http://" (:restate-node this) ":8000")} ; <-- envoy
                       )))
    this)

  (invoke! [this _ op]
    (let [baseurl (:baseurl this)
          client (:conn this)
          url (str baseurl (ops/op->grpc-method op))
          request (ops/op->argument op)
          ]
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
  (RestateClient. nil nil nil nil))


