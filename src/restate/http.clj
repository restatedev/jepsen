(ns restate.http
  (:require [hato.client :as hc]))

(def client (hc/build-http-client {:version :http-2
                                   :connect-timeout 500}))

(defn defaults [client] {:http-client client
                         :content-type :json
                         :timeout 2000})
