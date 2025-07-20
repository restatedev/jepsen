; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.set-metadata-store
  "A set client backed by the Restate Metadata Store HTTP API.
  Note: restate-server must be compiled with the metadata-api feature."
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :refer [info]]
   [hato.client :as hc]
   [jepsen [client :as client]
    [checker :as checker]
    [generator :as gen]]
   [restate
    [util :as u]
    [http :as hu]]
   [restate.jepsen.checker.tail-ok :refer [all-nodes-ok-after-final-heal]]
   [restate.jepsen.set-ops :refer [r w]]
   [slingshot.slingshot :refer [try+]]))

(defrecord
 SetMetadatsStoreClient [key opts] client/Client

 (open! [this test node]
   (assoc this
          :node (str "n" (inc (.indexOf (:nodes test) node)))
          :endpoint (str (u/admin-url node opts) "/metadata/")
          :defaults (hu/defaults hu/client)
          :random (new java.util.Random)))

 (setup! [this _test]
   (info "Using service URL" (:endpoint this))
   (hc/put (str (:endpoint this) key)
           (merge (:defaults this)
                  {:body (json/generate-string #{})
                   :headers {"if-match" "*" "etag" "1"}})))

 (invoke! [this _test op]
   (try+
    (case (:f op)
      :read (assoc op
                   :type :ok,
                   :value (->>
                           (hc/get (str (:endpoint this) key)
                                   (:defaults this))
                           (:body)
                           (json/parse-string)
                           set)
                   :node (:node this))

      :add
      (let [[new-set stored-version]
            (let [res
                  (hc/get (str (:endpoint this) key)
                          (:defaults this))]
              [(conj (->> (json/parse-string (:body res)) set) (:value op))
               (parse-long (get-in res [:headers "etag"]))])]
        (hc/put (str (:endpoint this) key)
                (merge (:defaults this)
                       {:body (json/generate-string new-set)
                        :headers {"if-match" (str stored-version)
                                  "etag" (str (inc stored-version))}}))
        (assoc op :type :ok)))
    (catch [:status 412] {} (assoc op :type :fail :error :precondition-failed :node (:node this)))
    (catch java.net.http.HttpTimeoutException {} (assoc op :type :info :error :timeout :node (:node this)))
    (catch Object {}
      (info "Exception:" (:throwable &throw-context))
      (assoc op :type :info :error :unhandled-exception :node (:node this)))))

 (teardown! [_ _test])

 (close! [_ _test]))

(defn workload
  "Restate Metadata Store-backed Set test workload, using the replicated store backend"
  [opts]
  {:client    (SetMetadatsStoreClient. "jepsen-set" opts)
   :checker   (checker/compose {:set (checker/set-full {:linearizable? true})
                                :heal (all-nodes-ok-after-final-heal)})
   :generator (gen/reserve 5 (repeat (r)) (w))
   :heal-time 10})

(defn workload-s3
  "Restate Metadata Store-backed Set test workload, using the object-store backend with an S3 bucket"
  [opts]
  (let [metadata-bucket (:metadata-bucket opts)]
    (when (nil? metadata-bucket)
      (throw (IllegalArgumentException. "Required parameter missing: :metadata-bucket")))
    (merge (workload opts)
           {:workload-opts
            {:restate-config-toml "restate-server-s3-metadata.toml"
             :additional-env
             {:RESTATE_METADATA_CLIENT__PATH (str "s3://" metadata-bucket "/metadata-" (:unique-id opts))}}})))

(defn workload-gcs
  "Restate Metadata Store-backed Set test workload, using the object-store backend with a GCS bucket"
  [opts]
  (let [metadata-bucket (:metadata-bucket opts)
        access-key-id (:access-key-id opts)
        secret-access-key (:secret-access-key opts)]
    (when (nil? metadata-bucket)
      (throw (IllegalArgumentException. "Required parameter missing: :metadata-bucket")))
    (when (nil? access-key-id)
      (throw (IllegalArgumentException. "Required parameter missing: :access-key-id (use --access-key-id or AWS_ACCESS_KEY_ID environment variable)")))
    (when (nil? secret-access-key)
      (throw (IllegalArgumentException. "Required parameter missing: :secret-access-key (use --secret-access-key or AWS_SECRET_ACCESS_KEY environment variable)")))
    (merge (workload opts)
           {:workload-opts
            {:restate-config-toml "restate-server-s3-metadata.toml"
             :additional-env
             {:RESTATE_METADATA_CLIENT__PATH (str "s3://" metadata-bucket "/metadata-" (:unique-id opts))
              :RESTATE_METADATA_CLIENT__AWS_ENDPOINT_URL "https://storage.googleapis.com"
              :RESTATE_METADATA_CLIENT__AWS_REGION "auto"
              :RESTATE_METADATA_CLIENT__AWS_ACCESS_KEY_ID access-key-id
              :RESTATE_METADATA_CLIENT__AWS_SECRET_ACCESS_KEY secret-access-key}}})))

;; TODO: setup of the Minio server itself is not yet automated, start a server on a node as follows:
;;
;;   mkdir -p /minio-data/restate
;;   docker run --detach --name minio -p 9000:9000 -p 9001:9001 -v /minio-data:/data quay.io/minio/minio server /data --console-address :9001
;;
;; Pass the address of the minio host to the test using: --s3-endpoint-url http://<address>:9000
(defn workload-minio
  "Restate Metadata Store-backed Set test workload, using the object-store backend with Minio"
  [opts]
  (let [metadata-bucket (:metadata-bucket opts)
        access-key-id (:access-key-id opts)
        secret-access-key (:secret-access-key opts)]
    (when (nil? metadata-bucket)
      (throw (IllegalArgumentException. "Required parameter missing: :metadata-bucket")))
    (when (nil? access-key-id)
      (throw (IllegalArgumentException. "Required parameter missing: :access-key-id (use --access-key-id or AWS_ACCESS_KEY_ID environment variable)")))
    (when (nil? secret-access-key)
      (throw (IllegalArgumentException. "Required parameter missing: :secret-access-key (use --secret-access-key or AWS_SECRET_ACCESS_KEY environment variable)")))
    (when (nil? (:s3-endpoint-url opts))
      (throw (IllegalArgumentException. "Required parameter missing: :s3-endpoint-url (use --s3-endpoint-url environment variable)")))
    (merge (workload opts)
           {:workload-opts
            {:restate-config-toml "restate-server-s3-metadata.toml"
             :additional-env
             {:RESTATE_METADATA_CLIENT__PATH (str "s3://" metadata-bucket "/metadata-" (:unique-id opts))
              :RESTATE_METADATA_CLIENT__AWS_ENDPOINT_URL (:s3-endpoint-url opts)
              :RESTATE_METADATA_CLIENT__AWS_REGION "minio"
              :RESTATE_METADATA_CLIENT__AWS_ALLOW_HTTP "true"
              :RESTATE_METADATA_CLIENT__AWS_ACCESS_KEY_ID access-key-id
              :RESTATE_METADATA_CLIENT__AWS_SECRET_ACCESS_KEY secret-access-key}}})))
