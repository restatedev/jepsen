; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.test
  (:require [clojure.test :refer :all]
            [restate.jepsen :refer [aws-creds get-env]]
            [restate.jepsen.set-metadata-store :as set-mds]))

(deftest some-test
  (testing "tautological"
    (is (= 1 1))))

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
  (testing "workload-gcs throws error when access-key-id is nil"
    (is (thrown-with-msg? IllegalArgumentException
                          #"Required parameter missing: :access-key-id"
                          (set-mds/workload-gcs {:metadata-bucket "test-bucket"
                                                 :unique-id "test-id"
                                                 :access-key-id nil
                                                 :secret-access-key "test-secret"}))))

  (testing "workload-gcs throws error when secret-access-key is nil"
    (is (thrown-with-msg? IllegalArgumentException
                          #"Required parameter missing: :secret-access-key"
                          (set-mds/workload-gcs {:metadata-bucket "test-bucket"
                                                 :unique-id "test-id"
                                                 :access-key-id "test-key"
                                                 :secret-access-key nil}))))

  (testing "workload-gcs succeeds when all required parameters are provided"
    (is (some? (set-mds/workload-gcs {:metadata-bucket "test-bucket"
                                      :unique-id "test-id"
                                      :access-key-id "test-key"
                                      :secret-access-key "test-secret"})))))
