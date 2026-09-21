/*
 * Copyright (c) 2023-2026 - Restate Software, Inc., Restate GmbH
 *
 * This file is part of the Restate Jepsen test suite,
 * which is released under the MIT license.
 *
 * You can find a copy of the license in file LICENSE in the root
 * directory of this repository or package, or at
 * https://github.com/restatedev/jepsen/blob/main/LICENSE
 */

// A GCS bucket for the object-store metadata workloads, plus a service account whose
// key the Jepsen worker nodes (which run in AWS) use to reach it. Each test run writes
// under a unique prefix, so the bucket is shared across runs and objects are expired by
// a lifecycle rule rather than cleaned up per run.

terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project
  region  = var.region
}

resource "google_storage_bucket" "metadata" {
  name          = var.bucket_name
  location      = var.region
  storage_class = "STANDARD"
  force_destroy = true

  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  lifecycle_rule {
    condition {
      age = var.object_expiry_days
    }
    action {
      type = "Delete"
    }
  }

  labels = {
    purpose = "jepsen-tests"
  }
}

resource "google_service_account" "jepsen" {
  account_id   = var.service_account_id
  display_name = "Restate Jepsen test workers"
  description  = "Used by Jepsen worker nodes running in AWS to access the metadata bucket"
}

resource "google_storage_bucket_iam_member" "jepsen_object_admin" {
  bucket = google_storage_bucket.metadata.name
  role   = "roles/storage.objectAdmin"
  member = google_service_account.jepsen.member
}

// The key ends up in the Terraform state; keep that state private. The JSON is what the
// Jepsen runner passes via --gcp-credentials-file (locally) or the GCP_CREDENTIALS
// GitHub Actions secret (CI).
resource "google_service_account_key" "jepsen" {
  service_account_id = google_service_account.jepsen.name
}
