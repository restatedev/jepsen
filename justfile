make-services:
  #!/usr/bin/env bash
  set -e
  cd services
  npm clean-install
  npm run bundle

create-aws-cluster stack-name="" allow-source-cidr="0.0.0.0/0":
  #!/usr/bin/env bash
  set -e
  cd aws
  npm clean-install
  npm run deploy -- --context stack-name={{stack-name}} --context allow-source-cidr={{allow-source-cidr}}
  bash get-node-info.sh

destroy-aws-cluster stack-name="":
  #!/usr/bin/env bash
  set -e
  cd aws
  npm run destroy -- --context stack-name={{stack-name}}

run-test workload="set-vo" nemesis="partition-random-node" image="ghcr.io/restatedev/restate:main":
  #!/usr/bin/env bash
  set -e
  # NB: we should use unique prefixes for each test run so that we don't have to wipe the bucket contents
  lein run test --nodes-file aws/nodes.txt --username admin --ssh-private-key aws/private-key.pem \
    --image {{image}} \
    --metadata-bucket "$(jq -r 'keys[0] as $stack_name | .[$stack_name].BucketName' aws/cdk-outputs.json)" \
    --leave-db-running true \
    --time-limit 120 --rate 10 --concurrency 5n --test-count 1 \
    --workload {{workload}} --nemesis {{nemesis}}
