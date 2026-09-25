# GCP resources for the Jepsen suite

This module manages the GCP side of the `set-mds-gcs` workload, which runs Restate's object-store metadata backend against a GCS bucket using a `gs://` path. The worker nodes themselves are EC2 instances created by the CDK stack in [aws](../aws).

Everything lives in the `restate-runtime-ci` project, which is reserved for generic runtime validation and kept apart from the cloud dev and prod projects.

| Resource | Managed by | Purpose |
| --- | --- | --- |
| `restate-jepsen-tests-us-east4` bucket | this module | Metadata written by test runs, under a unique prefix per run, expired after 7 days |
| `restate-jepsen-tests` service account | this module | The identity Restate uses on the worker nodes |
| `jepsenMetadataObjectStore` custom role | this module | Object get, create and delete, bound on the test bucket only |
| Service account key | `just` recipes | Stored in the `GCP_CREDENTIALS` secret of restatedev/jepsen |
| `restate-runtime-ci-tfstate` bucket | by hand | Terraform state, under one prefix per stack (`jepsen` here) |

The bucket is in `us-east4` because it is in the same metro as the `us-east-1` cluster.

## Applying changes

The recipes and the Terraform Google provider authenticate with your Application Default Credentials:

```shell
gcloud auth application-default login
just create-gcs-bucket
```

`create-gcs-bucket` runs `terraform init` and `apply`, and the state is shared through the state bucket, so anyone with access to the project can pick up where the last person left off.

## Service account key

The key is not a Terraform resource, so the state contains no secrets. Mint a key directly into the GitHub secret, without writing it to disk, or into a local `gcp-credentials.json` (gitignored) for runs from your machine:

```shell
just gcp-key-to-github
just gcp-key-file
```

To rotate, mint a new key, then delete the superseded ones that `just gcp-keys` lists.

A key is needed because the worker nodes run outside GCP. Restate's GCS client (the `object_store` crate) accepts a service account key, an Application Default Credentials file, or Compute Engine instance metadata. It has no support for workload identity federation.

How the key travels at test time:

1. In CI, [run-tests](../.github/actions/run-tests/action.yml) writes the secret to a file only the runner user can read, and deletes it when the step exits.
2. The runner passes the file with `--gcp-credentials-file`. Setup uploads it to each node as a root-only file and bind-mounts it read-only into the Restate container; see `upload-mounted-files` in [jepsen.clj](../src/restate/jepsen.clj).
3. The workload sets `GOOGLE_APPLICATION_CREDENTIALS` to the mounted path; see `workload-gcs` in [set_metadata_store.clj](../src/restate/jepsen/set_metadata_store.clj).

Only the file's path ends up in the Jepsen test map, which Jepsen logs and stores with the results.

## State bucket

The state bucket cannot hold its own state, so it was created once by hand. It is versioned and keeps the ten most recent old versions of each object:

```shell
gcloud storage buckets create gs://restate-runtime-ci-tfstate --project restate-runtime-ci \
  --location us-east4 --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://restate-runtime-ci-tfstate --versioning
```

New stacks in the project should use the same bucket with their own `prefix` in the `gcs` backend block.
