(ns jepsen.http-client
  (:require [clojure.data.json :as json])
  (:import [java.net.http HttpClient HttpRequest HttpClient$Version HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers]
           [java.net URI]))


(defn make
  []
  (-> (HttpClient/newBuilder)
      (.version HttpClient$Version/HTTP_2)
      (.build)))

(defn post [^HttpClient client ^String url body]
  (let [request-body (json/write-str body)
        request
        (-> (HttpRequest/newBuilder)
            (.uri (URI/create url))
            (.header "Accept" "application/json")
            (.header "Content-Type" "application/json")
            (.POST (HttpRequest$BodyPublishers/ofString request-body))
            (.build))
        response (^HttpResponse .send client request (HttpResponse$BodyHandlers/ofString))
        ]
    (if (not (= (.statusCode response) 200))
      {:success false :status (.statusCode response) :body nil}
      {:success true :status 200 :body (json/read-str (.body response))}))
  )
