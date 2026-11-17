; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen.os
  "Prepare every worker before database setup can enter its synchronization barrier."
  (:require [clojure.tools.logging :refer [info]]
            [jepsen.control :as control]
            [jepsen.os :as os]
            [jepsen.os.debian :as debian]))

(def os
  (reify os/OS
    (setup! [_ test node]
      (os/setup! debian/os test node)
      (info node "Installing worker runtime packages")
      (control/su
       (control/exec :apt :install :-y :podman :podman-docker :nodejs :jq)
       (control/exec :touch "/etc/containers/nodocker"))
      (info node "Worker runtime packages ready"))
    (teardown! [_ test node]
      (os/teardown! debian/os test node))))
