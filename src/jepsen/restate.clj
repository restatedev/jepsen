(ns jepsen.restate
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [client :as client]
             [generator :as gen]
             [tests :as tests]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [clj-http.client :as http]
            )
  )


 
(defn db
  "Restate for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing restate" version)
      (c/su
        (c/exec :systemctl :restart :docker)
        (c/exec :docker :pull "ghcr.io/restatedev/restate:latest")
        (c/exec :docker :run :-itd :--rm :-p "8081:8081" :-p "9090:9090" "ghcr.io/restatedev/restate:latest"))
      )

    (teardown! [_ test node]
      (info node "tearing down restate")
      (c/su
        (c/exec :docker :ps :-aq :| :xargs :docker :stop :|| true))
      )
    ))


(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})


(defn do-read [url op]
  (let [result false 
              ;(http/post (str url "/Read") 
               ;  {:form-params {:foo "foo" :bar "bar"} 
               ;   :content-type :json
               ;   :accept :json})
        ]
    (assoc op :type :ok :value 1337)
  ))

(defn do-write [url op]
  (assoc op :type :ok)
  )


(defn do-cas [url op]
  (assoc op :type :ok)
  )


(defrecord Client [conn]
  client/Client
  (open! [this test node]
    (assoc this :conn (str  "http://" node ":8080/dev.restate.JepsenService")))

  (setup! [this test])

  (invoke! [this test op]
   (case (:f op)
        :read (do-read (:conn this) op)  
        :write (do-write (:conn this) op)
        :cas (do-cas (:conn this) op))

        )

  (teardown! [this test])

  (close! [_ test]
    ; If our connection were stateful, we'd close it here.
    ))


(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {:name "restate"
          :os   debian/os
          :db   (db "v3.1.5")
          :pure-generators true
          :client          (Client. nil)
          :generator       (->> (gen/mix [r w cas])
                                (gen/stagger 1)
                                (gen/nemesis nil)
                                (gen/time-limit 15))
          
          }
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn restate-test})
            args))

