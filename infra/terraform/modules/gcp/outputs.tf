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
