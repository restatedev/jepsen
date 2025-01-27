# Jepsen tests for Restate

Build the services distribution first:

```shell
cd services && npm run bundle
```

Start test with:

```shell
lein run test --time-limit 60 --concurrency 10 --rate 10 --workload register-vo --node n1
```

### Local test development

For rapid iteration on test workloads, disabling SSH will skip the node setup actions. Start Restate and deploy any necessary services, then:

```shell
lein run test --node localhost --no-ssh true --time-limit 10 --rate 10 --concurrency 10 --workload register-vo 
```
