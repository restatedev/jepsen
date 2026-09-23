#!/usr/bin/env bash
#
# Checks that a metadata key written by the test exists in the external store the workload
# configured, using the same credentials Restate uses on this node. Exits non-zero, with the
# reason on stderr, if it does not.
#
# Usage:
#   verify-metadata.sh s3 <bucket> <object-key>
#   verify-metadata.sh dynamodb <table> <partition-key>

set -euo pipefail

aws_cli_image="public.ecr.aws/aws-cli/aws-cli:latest"

imds_region() {
  local token
  token=$(curl -sSf -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  curl -sSf -H "X-aws-ec2-metadata-token: ${token}" http://169.254.169.254/latest/meta-data/placement/region
}

# Host networking lets the AWS CLI reach the instance metadata service, like the Restate container.
aws_cli() {
  docker run --rm --network=host "${aws_cli_image}" --region "$(imds_region)" --output json "$@"
}

store=$1
shift
case "${store}" in
  s3)
    aws_cli s3api head-object --bucket "$1" --key "$2" > /dev/null
    ;;
  dynamodb)
    aws_cli dynamodb get-item --table-name "$1" --projection-expression pk \
      --key "$(jq -nc --arg pk "$2" '{pk: {S: $pk}}')" | jq -e .Item > /dev/null ||
      { echo "item ${2} not found in table ${1}" >&2; exit 1; }
    ;;
  *)
    echo "unknown store: ${store}" >&2
    exit 2
    ;;
esac
