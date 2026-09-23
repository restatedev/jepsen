output "bucket_name" {
  description = "Pass to the Jepsen runner as --gcs-bucket"
  value       = google_storage_bucket.metadata.name
}

output "service_account_email" {
  value = google_service_account.jepsen.email
}
