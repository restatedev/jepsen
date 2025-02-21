; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.http
  (:require [hato.client :as hc]))

(def client (hc/build-http-client {:version :http-2
                                   :connect-timeout 500}))

(defn defaults [client] {:http-client client
                         :content-type :json
                         :timeout 2000})
