; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.test
  (:require [clojure.test :refer :all]
            [restate.jepsen.common :refer [aws-creds get-env]]
            [jepsen.checker :as checker]
            [restate.jepsen.metadata-backend :as metadata-backend]
            [restate.jepsen.set-metadata-store :as set-mds]))

(deftest aws-creds-test
  (testing "CLI opts take precedence over environment variables"
    (with-redefs [get-env (fn [var]
                            (case var
                              "AWS_ACCESS_KEY_ID" "env-access-key"
                              "AWS_SECRET_ACCESS_KEY" "env-secret-key"
                              nil))]
      (let [opts {:access-key-id "cli-access-key"
                  :secret-access-key "cli-secret-key"}
            result (aws-creds opts)]
        (is (= "cli-access-key" (:access-key-id result)))
        (is (= "cli-secret-key" (:secret-access-key result))))))

  (testing "Environment variables are used when CLI opts not provided"
    (with-redefs [get-env (fn [var]
                            (case var
                              "AWS_ACCESS_KEY_ID" "env-access-key"
                              "AWS_SECRET_ACCESS_KEY" "env-secret-key"
                              nil))]
      (let [opts {}
            result (aws-creds opts)]
        (is (= "env-access-key" (:access-key-id result)))
        (is (= "env-secret-key" (:secret-access-key result))))))

  (testing "Returns nil when neither CLI opts nor env vars are available"
    (with-redefs [get-env (fn [var] nil)]
      (let [opts {}
            result (aws-creds opts)]
        (is (= nil (:access-key-id result)))
        (is (= nil (:secret-access-key result))))))

  (testing "Mixed precedence - CLI access key with env secret key"
    (with-redefs [get-env (fn [var]
                            (case var
                              "AWS_ACCESS_KEY_ID" "env-access-key"
                              "AWS_SECRET_ACCESS_KEY" "env-secret-key"
                              nil))]
      (let [opts {:access-key-id "cli-access-key"}
            result (aws-creds opts)]
        (is (= "cli-access-key" (:access-key-id result)))
        (is (= "env-secret-key" (:secret-access-key result)))))))

(deftest workload-gcs-validation-test
  (let [credentials-file (doto (java.io.File/createTempFile "gcp-credentials" ".json") .deleteOnExit)]
    (spit credentials-file "{}")

    (testing "workload-gcs requires a bucket"
      (is (thrown-with-msg? IllegalArgumentException
                            #"Required parameter missing: :gcs-bucket"
                            (set-mds/workload-gcs {:unique-id "test-id"
                                                   :gcp-credentials-file (.getPath credentials-file)}))))

    (testing "workload-gcs requires a credentials file"
      (is (thrown-with-msg? IllegalArgumentException
                            #"Required parameter missing: :gcp-credentials-file"
                            (set-mds/workload-gcs {:gcs-bucket "test-bucket"
                                                   :unique-id "test-id"}))))

    (testing "workload-gcs rejects a missing credentials file"
      (is (thrown-with-msg? IllegalArgumentException
                            #"GCP credentials file not found"
                            (set-mds/workload-gcs {:gcs-bucket "test-bucket"
                                                   :unique-id "test-id"
                                                   :gcp-credentials-file "/nonexistent/key.json"}))))

    (testing "workload-gcs mounts the credentials file and points Restate at the bucket"
      (let [workload-opts (:workload-opts (set-mds/workload-gcs {:gcs-bucket "test-bucket"
                                                                 :unique-id "test-id"
                                                                 :gcp-credentials-file (.getPath credentials-file)}))]
        (is (= {(.getPath credentials-file) set-mds/gcp-credentials-mount-path}
               (:mounted-files workload-opts)))
        (is (= "gs://test-bucket/metadata-test-id"
               (get-in workload-opts [:additional-env :RESTATE_METADATA_CLIENT__PATH])))
        (is (= set-mds/gcp-credentials-mount-path
               (get-in workload-opts [:additional-env :GOOGLE_APPLICATION_CREDENTIALS])))))))

(defn- metadata-client-type
  "The metadata client type a workload configures, via its environment or its config file."
  [workload]
  (let [{:keys [additional-env restate-config-toml]} (:workload-opts workload)]
    (or (:RESTATE_METADATA_CLIENT__TYPE additional-env)
        (second (re-find #"(?m)^type = \"([^\"]+)\"" (slurp (str "resources/" restate-config-toml)))))))

(deftest external-metadata-workloads-select-their-backend-test
  ;; Without an explicit type, Restate falls back to the replicated metadata store and ignores
  ;; the object-store path, so these workloads would silently test the wrong backend.
  (let [opts {:unique-id "test-id"
              :metadata-bucket "test-bucket"
              :dynamodb-table "test-table"
              :access-key-id "test-key"
              :secret-access-key "test-secret"
              :s3-endpoint-url "http://minio:9000"}]
    (is (= "object-store" (metadata-client-type (set-mds/workload-s3 opts))))
    (is (= "object-store" (metadata-client-type (set-mds/workload-gcs
                                                  (assoc opts
                                                         :gcs-bucket "test-bucket"
                                                         :gcp-credentials-file (.getPath (doto (java.io.File/createTempFile "gcp-credentials" ".json") .deleteOnExit)))))))
    (is (= "object-store" (metadata-client-type (set-mds/workload-minio opts))))
    (is (= "dynamo-db" (metadata-client-type (set-mds/workload-ddb opts))))))

(deftest workloads-declare-their-metadata-backend-test
  (let [opts {:unique-id "test-id" :metadata-bucket "test-bucket" :dynamodb-table "test-table"}]
    (is (= "s3://test-bucket/metadata-test-id/jepsen-set"
           (metadata-backend/location (:metadata-backend (set-mds/workload-s3 opts)))))
    (is (= "dynamodb://test-table/test-id_jepsen-set"
           (metadata-backend/location (:metadata-backend (set-mds/workload-ddb opts)))))
    (is (= "gs://test-bucket/metadata-test-id/jepsen-set"
           (metadata-backend/location
            (:metadata-backend (set-mds/workload-gcs
                                (assoc opts
                                       :gcs-bucket "test-bucket"
                                       :gcp-credentials-file (.getPath (doto (java.io.File/createTempFile "gcp-credentials" ".json") .deleteOnExit))))))))
    (is (nil? (:metadata-backend (set-mds/workload opts))))))

(deftest metadata-backend-checker-test
  (let [backend {:store :s3 :bucket "b" :key "k"}
        check (fn [history] (checker/check (metadata-backend/checker backend) {} history {}))
        verify-op (fn [value] {:process :nemesis :type :info :f :verify-metadata-backend :value value})
        client-ops [{:process 0 :type :invoke :f :read} {:process 0 :type :ok :f :read :value #{}}]]
    (testing "passes when the lookup found the key"
      (is (= {:valid? true :location "s3://b/k"}
             (check (concat client-ops [(verify-op nil) (verify-op {:found? true})])))))
    (testing "fails when the lookup did not find the key"
      (is (= {:valid? false :location "s3://b/k" :error "404"}
             (check (concat client-ops [(verify-op nil) (verify-op {:found? false :error "404"})])))))
    (testing "fails when no lookup happened"
      (is (false? (:valid? (check client-ops)))))))
