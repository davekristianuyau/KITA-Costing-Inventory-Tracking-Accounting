output "gateway_url" {
  value = local.use_tls ? "https://${var.custom_domain}" : "http://${aws_lb.main.dns_name}"
}

output "aggregate_health_url" {
  value = "${local.use_tls ? "https://${var.custom_domain}" : "http://${aws_lb.main.dns_name}"}${var.release_set[local.gateway_key].health_path}"
}

output "service_endpoints" {
  value = local.internal_urls
}

output "environment_name" {
  value = local.name
}
