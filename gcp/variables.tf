variable "project" {
  description = "GCP project that hosts the bucket and service account"
  type        = string
}

variable "region" {
  description = "Bucket location; us-east4 (Northern Virginia) is co-located with the AWS us-east-1 Jepsen cluster"
  type        = string
  default     = "us-east4"
}

variable "bucket_name" {
  description = "Globally unique name for the metadata bucket"
  type        = string
  default     = "restate-jepsen-tests-us-east4"
}

variable "service_account_id" {
  description = "Account id (local part of the email) of the service account granted access to the bucket"
  type        = string
  default     = "restate-jepsen-tests"
}

variable "object_expiry_days" {
  description = "Age in days after which leftover test objects are deleted"
  type        = number
  default     = 7
}
