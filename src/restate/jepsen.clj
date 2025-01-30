(ns restate.jepsen
  (:require
   [clojure.tools.logging :refer [info]]
   [clojure.math :as m]
   [clojure.string :as str]
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
   [restate.util :as u]
   [restate.jepsen.set-metadata-store :as set-mds]
   [restate.jepsen.set-virtual-object :as set-vo]
   [restate.jepsen.register-metadata-store :as register-mds]
   [restate.jepsen.register-virtual-object :as register-vo]))

(def resources-relative-path ".")
(def server-restate-root "/opt/restate/")
(def server-logfile (str server-restate-root "restate.log"))
(def server-services-dir "/opt/services/")
(def node-binary "/usr/bin/node")
(def services-args (str server-services-dir "services.js"))
(def services-pidfile (str server-services-dir "services.pid"))
(def services-logfile (str server-services-dir "services.log"))

(defn app-service-url [opts]
  (case (:dedicated-service-nodes opts)
    ;; homogeneous deployment - all nodes run SDK services, talk to localhost
    0 "http://host.docker.internal:9080"
    ;; heterogeneous deployment - currently only a single dedicated service node supported
    (str "http://" (last (:nodes opts)) ":9080")))

(defn cluster-setup
  "Cluster setup. Handles homogeneous and heterogeneous Restate/app server clusters."
  [restate-server-setup app-server-setup]
  (reify
    db/DB
    (setup! [_this test node]
      (when (u/app-server-node? node test)
        (db/setup! app-server-setup test node))
      (when (u/restate-server-node? node test)
        (db/setup! restate-server-setup test node)))
    (teardown! [_this test node]
      (db/teardown! app-server-setup test node)
      (db/teardown! restate-server-setup test node))

    db/LogFiles
    (log-files [_this test node]
      (db/log-files app-server-setup test node)
      (db/log-files restate-server-setup test node))))

(defn load-and-tag-docker-image [path image-tag]
  (let [output   (c/exec :docker :load :--input path)
        image-id (second (re-find #"Loaded image: (.+)" output))]
    (c/exec :docker :tag image-id image-tag)
    (c/exec :docker :image :ls :-a)))

(defn restate
  "A deployment of Restate server."
  [opts]
  (reify
    db/DB
    (setup! [_db test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Setting up Restate")
        (c/su
         (c/exec :apt :install :-y :docker.io :nodejs :jq)
         (c/exec :mkdir :-p "/opt/services")

         (when (:image-tarball test)
           (info node "Uploading Docker image" (:image-tarball test) "...")
           (c/upload (:image-tarball test) "/opt/restate.tar")
           (load-and-tag-docker-image "/opt/restate.tar" (:image test))
           (c/exec :docker :tag (:image test) "restate"))

         (c/upload (str resources-relative-path "/resources/restate-server.toml") "/opt/restate/config.toml")
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
            :--name=restate
            :--network=host ;; we need this to access AWS IMDS credentials on EC2
            :--add-host :host.docker.internal:host-gateway
            :--detach
            :--volume "/opt/restate/config.toml:/config.toml"
            :--volume "/opt/restate/restate-data:/restate-data"
            :--env (str "RESTATE_BOOTSTRAP_NUM_PARTITIONS=" (:num-partitions opts))
            :--env (str "RESTATE_METADATA_STORE_CLIENT__ADDRESSES=" metadata-addresses)
            :--env (str "RESTATE_ADVERTISED_ADDRESS=http://" node ":5122")
            :--env (str "RESTATE_BIFROST__REPLICATED_LOGLET__DEFAULT_REPLICATION_PROPERTY={node: " replication-factor "}")
            :--env "DO_NOT_TRACK=true"
            (:image test)
            :--node-name node-name
            :--force-node-id node-id
            :--allow-bootstrap (if (= node (first (:nodes test))) "true" "false")
            :--auto-provision-partitions (if (= node (first (:nodes test))) "true" "false")
            :--config-file "/config.toml"
            ;; :--metadata-store-address metadata-store-address ;; TODO: this doesn't seem to have an effect
            ;; :--advertise-address (str "http://" node ":5122") ;; TODO: this doesn't seem to have an effect
            ))
         (u/await-url "http://localhost:9070/health")

         (info "Waiting for all nodes to join cluster and partitions to be configured...")
         (u/wait-for-metadata-servers (u/restate-server-node-count opts))
         (u/wait-for-logs (:num-partitions opts))
         (u/wait-for-partition-leaders (:num-partitions opts))
         (u/wait-for-partition-followers (* (:num-partitions opts) (dec (u/restate-server-node-count opts))))

         (when (= node (first (:nodes test)))
           (info "Performing once-off setup")
           (when (> (:dedicated-service-nodes opts) 0) (u/await-tcp-port (last (:nodes opts)) 9080))
           (u/restate :deployments :register (app-service-url opts) :--yes))

         (u/await-service-deployment))))

    (teardown! [_this test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Tearing down Restate")
        (c/su
         (c/exec :rm :-rf server-restate-root)
         (c/exec :docker :rm :-f "restate" :|| :true))))

    db/LogFiles
    (log-files [_this test _node]
      (when (not (:dummy? (:ssh test)))
        (c/su (c/exec* "docker logs restate" "&>" server-logfile)
              (c/exec :chmod :644 server-logfile))
        [server-logfile services-logfile]))))

(defn app-server
  "A deployment of a Restate application (= set of Restate SDK services)."
  [_opts]
  (reify db/DB
    (setup! [_db test node]
      (when (not (:dummy? (:ssh test)))
        (info node "Setting up services")
        (c/su
         (c/exec :apt :install :-y :docker.io :nodejs :jq)
         (c/exec :mkdir :-p "/opt/services")

         (c/upload (str resources-relative-path "/services/dist/services.zip") "/opt/services.zip")
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
         (c/exec :rm :-rf server-services-dir))))

    db/LogFiles (log-files [_this test _node]
                  (when (not (:dummy? (:ssh test)))
                    [services-logfile]))))

(def workloads
  "A map of workloads."
  {"set-mds"      set-mds/workload
   "set-vo"       set-vo/workload
   "register-mds" register-mds/workload
   "register-vo"  register-vo/workload})

(def nemeses
  "A map of nemeses."
  {"none"                  nemesis/noop
   "container-killer"      (nemesis/node-start-stopper
                            rand-nth
                            (fn start [_t _n]
                              (c/su (c/exec :docker :kill :-s :KILL "restate"))
                              [:killed "restate-server"])
                            (fn stop [_t _n]
                              (c/su (c/exec :docker :start "restate"))
                              [:restarted "restate-server"]))
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
           (if (not (:dummy? (:ssh opts))) {:os debian/os} nil)
           {:pure-generators true
            :name            (str "restate-" (name (:workload opts)))
            :db              (cluster-setup (restate opts) (app-server opts))
            :client          (:client workload)
            :nemesis         (get nemeses (:nemesis opts))
            :generator       (if (= (:nemesis opts) "none")
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
                                (->> (:generator workload)
                                     (gen/stagger (/ (:rate opts)))
                                     (gen/time-limit 5))))
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
   ["-w" "--workload NAME" "Workload to run"
    :missing  (str "--workload " (cli/one-of workloads))
    :validate (workloads (cli/one-of workloads))]
   ["-N" "--nemesis NAME" "Nemesis to apply"
    :default "none"
    :validate (nemeses (cli/one-of nemeses))]
   ;; By default the cluster is homegeneous - all nodes run restate-server as well as the SDK services.
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
