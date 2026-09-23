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

// A GCS bucket for the object-store metadata workloads, plus a service account that the
// Jepsen worker nodes (which run in AWS) authenticate as. Each test run writes under a
// unique prefix, so the bucket is shared across runs and objects are expired by a
// lifecycle rule rather than cleaned up per run.
//
// The service account key is deliberately not managed here, so that the Terraform state
// holds no secrets; mint it with `just gcp-key-to-github` or `just gcp-key-file`.

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

  // User Application Default Credentials need a quota project for these APIs.
  billing_project       = var.project
  user_project_override = true
}

resource "google_project_service" "apis" {
  for_each = toset(["cloudresourcemanager.googleapis.com", "iam.googleapis.com", "storage.googleapis.com"])

  service            = each.value
  disable_on_destroy = false
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

  depends_on = [google_project_service.apis]
}

resource "google_service_account" "jepsen" {
  account_id   = var.service_account_id
  display_name = "Restate Jepsen test workers"
  description  = "Used by Jepsen worker nodes running in AWS to access the metadata bucket"

  depends_on = [google_project_service.apis]
}

// The object-store metadata backend only reads objects and writes them with generation
// preconditions. GCS requires storage.objects.delete to overwrite an existing object, so
// that is included; listing, object ACLs and bucket access are not.
resource "google_project_iam_custom_role" "metadata_object_store" {
  role_id     = "jepsenMetadataObjectStore"
  title       = "Jepsen metadata object store"
  description = "Read, create and overwrite objects; bound on the Jepsen metadata bucket only"
  permissions = [
    "storage.objects.create",
    "storage.objects.delete",
    "storage.objects.get",
  ]

  depends_on = [google_project_service.apis]
}

resource "google_storage_bucket_iam_member" "jepsen" {
  bucket = google_storage_bucket.metadata.name
  role   = google_project_iam_custom_role.metadata_object_store.id
  member = google_service_account.jepsen.member
}
