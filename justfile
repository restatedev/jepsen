make-services:
  #!/usr/bin/env bash
  set -e
  cd services
  npm clean-install
  npm run bundle

create-aws-cluster stack-name="" allow-source-cidr="0.0.0.0/0" bucket-name="" table-name="":
  #!/usr/bin/env bash
  set -e
  cd aws
  npm clean-install
  npm run deploy -- \
    --context stack-name={{stack-name}} \
    --context allow-source-cidr={{allow-source-cidr}} \
    --context bucket-name={{bucket-name}} \
    --context table-name={{table-name}}
  bash get-node-info.sh

destroy-aws-cluster stack-name="" bucket-name="" table-name="":
  #!/usr/bin/env bash
  set -e
  cd aws
  npm run destroy --\
    --context stack-name={{stack-name}} \
    --context bucket-name={{bucket-name}} \
    --context table-name={{table-name}}

gcp-project := "restate-runtime-ci"
gcp-service-account := "restate-jepsen-tests@" + gcp-project + ".iam.gserviceaccount.com"

# Creates the GCS bucket, service account and least-privilege role for set-mds-gcs
create-gcs-bucket:
  cd gcp && terraform init -input=false && terraform apply -input=false -auto-approve

destroy-gcs-bucket:
  cd gcp && terraform destroy -input=false -auto-approve

# Mints a service account key directly into restatedev/jepsen's GCP_CREDENTIALS secret, without writing it to disk
gcp-key-to-github:
  #!/usr/bin/env bash
  set -euo pipefail
  key=$(just _mint-gcp-key)
  base64 --decode <<< "${key}" | gh secret set GCP_CREDENTIALS --repo restatedev/jepsen

# Mints a service account key for local test runs into gcp-credentials.json (gitignored)
gcp-key-file:
  #!/usr/bin/env bash
  set -euo pipefail
  key=$(just _mint-gcp-key)
  (umask 077 && base64 --decode <<< "${key}" > gcp-credentials.json)

# Lists the service account's keys; delete superseded ones with `gcloud iam service-accounts keys delete`
gcp-keys:
  gcloud iam service-accounts keys list --managed-by=user --iam-account={{gcp-service-account}} --project={{gcp-project}}

_mint-gcp-key:
  #!/usr/bin/env bash
  set -euo pipefail
  curl -sSf -X POST \
    -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" \
    -H "x-goog-user-project: {{gcp-project}}" \
    "https://iam.googleapis.com/v1/projects/{{gcp-project}}/serviceAccounts/{{gcp-service-account}}/keys" \
    | jq -er .privateKeyData

run-test workload="set-vo" nemesis="partition-random-node" image="ghcr.io/restatedev/restate:main" gcs-bucket="":
  #!/usr/bin/env bash
  set -e
  GCS_ARGS=()
  if [ -n "{{gcs-bucket}}" ]; then
    GCS_ARGS=(--gcs-bucket "{{gcs-bucket}}" --gcp-credentials-file gcp-credentials.json)
  fi
  # NB: we should use unique prefixes for each test run so that we don't have to wipe the bucket contents
  lein run test --nodes-file aws/nodes.txt --username admin --ssh-private-key aws/private-key.pem \
    --image {{image}} \
    --dynamodb-table "$(jq -r 'keys[0] as $stack_name | .[$stack_name].DynamoDbMetadataTableName' aws/cdk-outputs.json)" \
    --metadata-bucket "$(jq -r 'keys[0] as $stack_name | .[$stack_name].BucketName' aws/cdk-outputs.json)" \
    --snapshot-bucket "$(jq -r 'keys[0] as $stack_name | .[$stack_name].BucketName' aws/cdk-outputs.json)" \
    "${GCS_ARGS[@]}" \
    --leave-db-running true \
    --time-limit 120 --rate 10 --concurrency 5n --test-count 1 \
    --workload {{workload}} --nemesis {{nemesis}}
