(ns jepsen.http-client
  (:require [clojure.data.json :as json])
  (:use [slingshot.slingshot :only [throw+ try+]])

  (:import (java.io IOException)
           (java.net ConnectException SocketTimeoutException URI)
           (java.net.http HttpClient HttpClient$Version HttpRequest HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers))
  )


(defn make
  []
  (-> (HttpClient/newBuilder)
      (.version HttpClient$Version/HTTP_2)
      (.build)))

(defn post [^HttpClient client ^String url body]
  (let [request-body (json/write-str body :escape-slash false)
        request
        (-> (HttpRequest/newBuilder)
            (.uri (URI/create url))
            (.header "Accept" "application/json")
            (.header "Content-Type" "application/json")
            (.POST (HttpRequest$BodyPublishers/ofString request-body))
            (.build))]
      (let [response (^HttpResponse .send client request (HttpResponse$BodyHandlers/ofString))]
        (if (not (= (.statusCode response) 200))
          {:success false :status (.statusCode response) :body nil}
          {:success true :status 200 :body (json/read-str (.body response) :key-fn clojure.core/keyword)}))))