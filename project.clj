(defproject jepsen.restate "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :main jepsen.restate
  :aot :all
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [jepsen "0.3.2"]
                 [verschlimmbesserung "0.1.3"]
                 [org.clojure/data.json "2.4.0"]
                 ]
  :repl-options {:init-ns jepsen.restate}

  :jvm-opts ["-Xmx8g"
             "-server"
             "-XX:+UseZGC"
             "-XX:-OmitStackTraceInFastThrow"]
  )
