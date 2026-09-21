; Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(defproject restate.jepsen "0.1.0-SNAPSHOT"
  :description "Restate Jepsen tests"
  :main restate.jepsen
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [jepsen/jepsen "0.3.13"]
                 [hato "1.0.0"]
                 [cheshire "6.2.0"]]
  :repl-options {:init-ns restate.jepsen}
  :jvm-opts ["-Xmx8g"
             "-server"
             "-XX:+UseZGC"
             "-XX:-OmitStackTraceInFastThrow"]
  :plugins [[lein-ancient "1.0.0-RC3"]])
