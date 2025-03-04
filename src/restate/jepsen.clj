; Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
;
; This file is part of the Restate Jepsen test suite,
; which is released under the MIT license.
;
; You can find a copy of the license in file LICENSE in the root
; directory of this repository or package, or at
; https://github.com/restatedev/jepsen/blob/main/LICENSE

(ns restate.jepsen
  (:require
   [clojure.math :as m]
   [clojure.string :as str]
   [clojure.tools.logging :refer [info]]
   [jepsen
    [checker :as checker]
    [cli :as cli]
    [control :as c]
    [db :as db]
    [generator :as gen]
    [nemesis :as nemesis]
    [tests :as tests]]
   [jepsen.checker.timeline :as timeline]
   [jepsen.control.util :as cu]
   [jepsen.os.debian :as debian]
   [restate.jepsen.register-metadata-store :as register-mds]
   [restate.jepsen.register-virtual-object :as register-vo]
   [restate.jepsen.set-metadata-store :as set-mds]
   [restate.jepsen.set-virtual-object :as set-vo]
   [restate.util :as u]))

(def restate-root "/opt/restate/")
(def restate-config (str restate-root "config.toml"))
(def restate-logfile (str restate-root "restate.log"))
(def services-root "/opt/services/")
(def services-args (str services-root "services.js"))
(def services-pidfile (str services-root "services.pid"))
(def services-logfile (str services-root "services.log"))
(def node-binary "/usr/bin/node")

(defn app-service-url [opts]
  (case (:dedicated-service-nodes opts)
    ;; homogeneous deployment - all nodes run SDK services, talk to localhost
    0 "http://localhost:9080"
    ;; heterogeneous deployment - currently only a single dedicated service node supported
    (str "http://" (last (:nodes opts)) ":9080")))

(defn cluster-setup
  "Cluster setup. Handles homogeneous and heterogeneous Restate/app server clusters."
  [restate-server-setup app-server-setup]
  (reify
    db/DB
    (setup! [_this test node]
      (when (not (:dummy? (:ssh test)))
        (c/su
         (c/exec :apt :install :-y :podman :podman-docker :nodejs :jq)
         (c/exec :touch "/etc/containers/nodocker")))
      (when (u/app-server-node? node test)
        (db/setup! app-server-setup test node))
      (when (u/restate-server-node? node test)
        (db/setup! restate-server-setup test node)))
    (teardown! [_this test node]
      (db/teardown! app-server-setup test node)
      (db/teardown! restate-server-setup test node))

    db/LogFiles
    (log-files [_this test node]
      (concat (db/log-files app-server-setup test node)
              (db/log-files restate-server-setup test node)))))

(defn load-and-tag-docker-image [path image-tag]
  (let [output   (c/exec :docker :load :--input path)
        image-id (second (re-find #"Loaded image: (.+)" output))]
    (c/exec :docker :tag image-id image-tag)
    (c/exec :docker :image :ls :-a)))

(defn docker-env
  "Turns a map into a sequence of Docker command line --env arguments"
  [env]
  (mapcat (fn [[k v]] ["--env" (str (name k) "=" v)]) env))

(defn restate
  "A deployment of Restate server."
  [opts]
  (reify
    db/DB
    (setup! [_db test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Setting up Restate")
        (c/su
         (c/exec :mkdir :-p (str restate-root "restate-data"))
         (c/exec :chmod 777 restate-root)
         (c/exec :ls :-l restate-root)

         (when (:image-tarball test)
           (info node "Uploading Docker image" (:image-tarball test) "to" node)
           (c/upload (:image-tarball test) "/opt/restate/restate.tar")
           (load-and-tag-docker-image "/opt/restate/restate.tar" (:image test))
           (c/exec :docker :tag (:image test) "restate"))

         (c/upload (str "resources/" (:restate-config-toml test)) restate-config)
         (info node "Starting Restate server container")
         (let [node-name (str "n" (inc (.indexOf (:nodes test) node)))
               node-id (inc (.indexOf (:nodes test) node))
               replication-factor (->>
                                   (/ (u/restate-server-node-count opts) 2) m/floor int inc)
               metadata-addresses (str "["
                                       (->> (u/restate-server-nodes opts)
                                            (map (fn [n] (str "http://" n ":5122")))
                                            (str/join ","))
                                       "]")]
           (c/exec
            :docker
            :run
            (str "--pull=" (:image-pull-policy opts))
            :--name=restate
            :--network=host ;; we need this to access AWS IMDS credentials on EC2
            ;; :--add-host :host.docker.internal:host-gateway ;; podman doesn't support this
            :--detach
            :--volume (str restate-config ":/config.toml")
            :--volume "/opt/restate/restate-data:/restate-data"
            (docker-env (merge {:RESTATE_DEFAULT_NUM_PARTITIONS (:num-partitions opts)
                                :RESTATE_METADATA_CLIENT__ADDRESSES metadata-addresses
                                :RESTATE_ADVERTISED_ADDRESS (str "http://" node ":5122")
                                :RESTATE_BIFROST__REPLICATED_LOGLET__DEFAULT_LOG_REPLICATION (str "{node: " replication-factor "}")
                                :DO_NOT_TRACK "true"}
                               (:additional-env test)))
            (:image test)
            :--cluster-name (:cluster-name test)
            :--node-name node-name
            :--force-node-id node-id
            :--auto-provision (if (= node-id 1) "true" "false")
            :--config-file "/config.toml"))

         (u/wait-for-container "restate")
         ;; (u/await-url "http://localhost:9070/health")

         (info "Waiting for all nodes to join cluster and partitions to be configured...")
         (u/wait-for-partition-leaders (:num-partitions opts))
         (u/wait-for-partition-followers (* (:num-partitions opts) (dec (u/restate-server-node-count opts))))
         (info "Hostname: " (c/exec :hostname))
         (info "Restate cluster status: " (c/exec :docker :exec :restate :restatectl :status))
         (info "Restate whoami: " (c/exec :docker :exec :restate :restate :whoami))

         (when (= node (first (:nodes test)))
           (info "Performing once-off setup")
           (when (> (:dedicated-service-nodes opts) 0) (u/await-tcp-port (last (:nodes opts)) 9080))
           (u/restate :deployments :register (app-service-url opts) :--yes))

         (u/await-service-deployment))))

    (teardown! [_this test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Tearing down Restate")
        (c/su
         (c/exec :rm :-rf restate-root)
         (c/exec :docker :rm :-f "restate" :|| :true))))

    db/LogFiles
    (log-files [_this test _node]
      (when (not (:dummy? (:ssh test)))
        (c/su (c/exec* "docker logs restate" "&>" restate-logfile "|| true")
              (c/exec :chmod :644 restate-logfile))
        [restate-logfile]))))

(defn app-server
  "A deployment of a Restate application (= set of Restate SDK services)."
  [_opts]
  (reify db/DB
    (setup! [_db test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Setting up services")
        (c/su
         (c/exec :mkdir :-p "/opt/services")
         (c/exec :chmod :777 "/opt/services")

         (c/upload "services/dist/services.zip" "/opt/services.zip")
         (cu/install-archive! "file:///opt/services.zip" "/opt/services")
         (c/exec :rm "/opt/services.zip")

         (cu/start-daemon!
          {:logfile services-logfile
           :pidfile services-pidfile
           :chdir   "/opt/services"}
          node-binary services-args)
         (cu/await-tcp-port 9080))))

    (teardown! [_this test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Tearing down services")
        (c/su
         (cu/stop-daemon! node-binary services-pidfile)
         (c/exec :rm :-rf services-root))))

    db/LogFiles
    (log-files [_this test _node]
      (when (not (:dummy? (:ssh test)))
        [services-logfile]))))

(def workloads
  "A map of workloads."
  {"set-mds"      set-mds/workload
   "set-mds-s3"   set-mds/workload-s3
   "set-vo"       set-vo/workload
   "register-mds" register-mds/workload
   "register-vo"  register-vo/workload})

(def nemeses
  "A map of nemeses."
  {"none"                  nemesis/noop
   "kill-random-node"      (nemesis/node-start-stopper
                            rand-nth
                            (fn start [_t _n]
                              (c/su (c/exec :docker :kill :-s :KILL "restate"))
                              [:killed "restate-server"])
                            (fn stop [_t _n]
                              (c/su (c/exec :docker :start "restate"))
                              [:restarted "restate-server"]))
   "pause-random-node"      (nemesis/node-start-stopper
                             rand-nth
                             (fn start [_t _n]
                               (c/su (c/exec :docker :kill :-s :STOP "restate"))
                               [:paused "restate-server"])
                             (fn stop [_t _n]
                               (c/su (c/exec :docker :kill :-s :CONT "restate"))
                               [:resumed "restate-server"]))
   "nuke-partition-state"  (nemesis/node-start-stopper
                            rand-nth
                            (fn start [_t _n]
                              (c/su (c/exec :docker :kill :-s :KILL "restate")
                                    (c/exec :rm :-rf "/opt/restate/restate-data/n1/db/"))
                              [:killed "restate-server"])
                            (fn stop [_t _n]
                              (c/su (c/exec :docker :start "restate"))
                              [:restarted "restate-server"]))
   "partition-random-node" (nemesis/partition-random-node)})

(defn restate-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency ...), constructs a test map. Special options:

      :rate         Approximate number of requests per second, per thread
      :ops-per-key  Maximum number of operations allowed on any given key.
      :workload     Type of workload.
      :nemesis      Nemesis to apply."
  [opts]
  (let [workload ((get workloads (:workload opts)) opts)]
    (merge tests/noop-test
           opts
           {:restate-config-toml "restate-server.toml"}
           (:workload-opts workload)
           (if (not (:dummy? (:ssh opts))) {:os debian/os} nil)
           {:pure-generators true
            :name            (str "restate-" (name (:workload opts)))
            :cluster-name    (str "jepsen-" (.format
                                             (java.text.SimpleDateFormat. "yyyyMMdd'T'HHmmss")
                                             (java.util.Date.)))
            :db              (cluster-setup (restate opts) (app-server opts))
            :client          (:client workload)
            :nemesis         (get nemeses (:nemesis opts))
            :generator       (if (or
                                  (= (:nemesis opts) "none")
                                  (nil? (:heal-time workload)))
                               (->> (:generator workload)
                                    (gen/stagger (/ (:rate opts)))
                                    (gen/time-limit (:time-limit opts))
                                    (gen/clients))
                               (gen/phases
                                (->> (:generator workload)
                                     (gen/stagger (/ (:rate opts)))
                                     (gen/nemesis (cycle [(gen/sleep 5) {:type :info, :f :start}
                                                          (gen/sleep 5) {:type :info, :f :stop}]))
                                     (gen/time-limit (:time-limit opts)))
                                (gen/log "Healing cluster")
                                (gen/once (gen/nemesis [{:type :info, :f :stop}]))
                                (gen/log "Running post-heal workload")
                                (->> (:generator workload)
                                     (gen/stagger (/ (:rate opts)))
                                     (gen/time-limit (:heal-time workload)))))
            :checker         (checker/compose
                              {:perf       (checker/perf)
                               :stats      (checker/stats)
                               :exceptions (checker/unhandled-exceptions)
                               :timeline   (timeline/html)
                               :workload   (:checker workload)})})))

(def cli-opts
  "Additional command line options."
  [["-i" "--image STRING" "Restate container version"
    :default "ghcr.io/restatedev/restate:main"]
   [nil "--image-tarball STRING" "Restate container local path"]
   [nil "--image-pull STRING" "Docker pull policy (missing | always | never)"
    :default "missing"]
   ["-w" "--workload NAME" "Workload to run"
    :missing  (str "--workload " (cli/one-of workloads))
    :validate (workloads (cli/one-of workloads))]
   ["-N" "--nemesis NAME" "Nemesis to apply"
    :default "none"
    :validate (nemeses (cli/one-of nemeses))]
   [nil "--metadata-bucket NAME" "[Optional] Bucket to use for object-store metadata backend"]
   ;; By default the cluster is homogeneous - all nodes run restate-server as well as the SDK services.
   ;; This option allows us to separate the SDK services to only run on dedicated nodes.
   [nil "--dedicated-service-nodes N" "Number of dedicated service hosting nodes."
    :default  0
    :parse-fn read-string
    :validate [#(and (number? %) (>= % 0) (<= % 1))
               "Must be a number between 0 and 1."]] ;; TODO: add load balancer support so we can go > 1
   [nil "--num-partitions N" "Number of partitions in the Restate cluster."
    :default  1
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   ["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default  10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--ops-per-key NUM" "Maximum number of operations on any given key."
    :default  100
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer."]]])

(defn -main
  "CLI entry point. Start test or web server for viewing results."
  [& args]
  (cli/run!
   (merge
    (cli/single-test-cmd {:test-fn restate-test :opt-spec cli-opts})
    (cli/serve-cmd))
   args))
