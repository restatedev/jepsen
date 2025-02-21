# Restate Jepsen test suite

This repository contains a set of Jepsen tests for validating [Restate](https://github.com/restatedev/restate) distributed operation.

The complete system-under-test includes both the Restate Server and the SDK services. Some of the workloads included in this test suite only target the Restate embedded Metadata Store, while others also involve specially crafted SDK services. Deploying the Restate cluster and the required services is handled by the test setup phase automatically.

## Building

Running Jepsen requires a recent JVM and Leiningen installed. On Mac, you can:

```shell
brew install leiningen
```

To build Restate services or to create an AWS cluster, you will also need npm:

```shell
just make-services
```

### Test infrastructure

Jepsen assumes that you have a set of nodes accessible over SSH. These nodes are expected to run Debian Linux. If you already have root-level access to suitable nodes, you can specify them directly via the `--node` parameter below.

The repository includes an AWS CDK stack which you can use to provision a cluster of worker nodes. There is currently no provision to create a control node. The host on which you start the Jepsen tests drives requests to Restate cluster, and ultimately performs the execution history analysis. By default the cluster is open to the world - you can configure basic settings such as the control node address, instance type, and cluster size in [cluster.ts](aws/cluster.ts). You can spin up a cluster as follows:

```shell
just create-aws-cluster
```

This stack only creates a cluster of worker nodes. When running the tests, consider where the control node - a development machine, a CI worker, another EC2 instance in the same region - is located relative to the worker nodes as this will influence client latency.

You can SSH to specific nodes using:

```shell
ssh -l admin -i aws/private-key.pem ${node}
```

To tear down the cluster after you're done, use:

```shell
just destroy-aws-cluster
```

### Running tests

To run a specific test against the AWS cluster:

```shell
lein run test --nodes-file aws/nodes.txt --username admin --ssh-private-key aws/private-key.pem \
  --image ghcr.io/restatedev/restate:main \
  --workload set-vo --nemesis partition-random-node \
  --time-limit 60 --rate 10 --concurrency 5n --leave-db-running true
```

Results will be available in `store/latest`, including collected logs from the worker nodes.

#### Custom Restate builds

The cluster workers are set up to start Restate from a Docker image. If the image you need to test is not available from a public repository, you can test a custom one. Build and export a suitable `restate` image for the target platform from your [restate](https://github.com/restatedev/restate) workspace using:

```shell
just --set arch x86_64 --set features metadata-api docker
docker save localhost/restatedev/restate:${revision} -o ../restate-${revision}.tar
```

**Note:** debug images will work but are an order of magnitude larger than release images! Consider that the Jepsen test driver will need to upload the exported image to each of the cluster nodes.

Then specify the image to the test runner as follows:

```shell
lein run test ... \
  --image localhost/restatedev/restate:${revision} \
  --image-tarball ../restate-${revision}.tar \
  ...
```

After the initial run, you can omit the `--image-tarball` argument as the image will be cached on the workers.

#### Workloads and fault injection

You can select the mode of operation via the `--workload` and `--nemesis` command line arguments.

Two principal workloads are currently available:

- `set-mds` (requires `restate-server` compiled with `metadata-api` feature)
- `set-vo` (requires the `Set` virtual object provided in this repository)

These both validate linearizability based on the included Jepsen set-append checker. Fault injection strategies currently supported include:

- `none` (default)
- `kill-random-node` (self-explanatory)
- `pause-random-node` (suspends a single node at a time using SIGSTOP)
- `partition-random-node` (isolates a single node at a time)

To see the full list of supported workloads and nemesis options, refer to [restate.jepsen](src/restate/jepsen.clj).

**Note:** Jepsen's checks are focused on correctness and finding consistency violations; it's possible that a test run trivially passes because all nodes returned a service error because of a misconfiguration.

### Local testing

For rapid iteration on test workloads on your local machine, disabling SSH will skip the node setup actions. Start Restate and deploy any necessary services, then:

```shell
lein run test --node localhost --no-ssh true --time-limit 10 --rate 10 --concurrency 10 --workload set-mds
```

In this mode, the test assumes that you have manually set up the target environment - started `restate-server`, deployed the needed services, etc. Similarly, the tear-down phase is also ignored so local processes will be left intact after the run.
