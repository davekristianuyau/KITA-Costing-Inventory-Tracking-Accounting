locals {
  gateway_base = var.custom_domain != "" ? "https://${var.custom_domain}" : google_cloud_run_v2_service.public[local.gateway_key].uri
}

output "gateway_url" {
  value = local.gateway_base
}

output "aggregate_health_url" {
  value = "${local.gateway_base}${var.release_set[local.gateway_key].health_path}"
}

output "service_endpoints" {
  value = merge(
    { for k, s in google_cloud_run_v2_service.backend : k => s.uri },
    { for k, s in google_cloud_run_v2_service.public : k => s.uri },
  )
}

output "environment_name" {
  value = local.name
}

output "db_connection_secret_ref" {
  value = google_secret_manager_secret.db_password.id
}

output "object_storage_ref" {
  value = google_storage_bucket.main.id
}

output "resource_ids" {
  value = merge(
    {
      network  = google_compute_network.vpc.id
      database = google_sql_database_instance.main.id
      bucket   = google_storage_bucket.main.id
    },
    { for k, s in google_cloud_run_v2_service.backend : "service-${k}" => s.id },
    { for k, s in google_cloud_run_v2_service.public : "service-${k}" => s.id },
  )
}
