make-services:
  #!/usr/bin/env bash
  cd services
  npm clean-install
  npm run bundle

create-aws-cluster allow-source-cidr="0.0.0.0/0":
  #!/usr/bin/env bash
  cd aws
  npm clean-install
  npm run deploy -- --context allow-source-cidr={{allow-source-cidr}}
  bash get-node-info.sh

destroy-aws-cluster:
  #!/usr/bin/env bash
  cd aws
  npm run destroy

run-test workload="set-vo" nemesis="partition-random-node" image="ghcr.io/restatedev/restate:main":
  lein run test --nodes-file aws/nodes.txt --username admin --ssh-private-key aws/private-key.pem \
    --image {{image}} \
    --leave-db-running true \
    --time-limit 120 --rate 10 --concurrency 5n --test-count 1 \
    --workload {{workload}} --nemesis {{nemesis}}
