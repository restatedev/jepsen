output "bucket_name" {
  description = "Pass to the Jepsen runner as --gcs-bucket"
  value       = google_storage_bucket.metadata.name
}

output "service_account_email" {
  value = google_service_account.jepsen.email
}

output "service_account_key_json" {
  description = "Service account key in JSON form; write it to a file for --gcp-credentials-file or store it as the GCP_CREDENTIALS GitHub Actions secret"
  value       = base64decode(google_service_account_key.jepsen.private_key)
  sensitive   = true
}
