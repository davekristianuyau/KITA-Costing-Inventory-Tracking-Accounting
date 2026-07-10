locals {
  gateway_fqdn = azurerm_container_app.public[local.gateway_key].ingress[0].fqdn
  gateway_base = var.custom_domain != "" ? "https://${var.custom_domain}" : "https://${local.gateway_fqdn}"
}

output "gateway_url" {
  value = local.gateway_base
}

output "aggregate_health_url" {
  value = "${local.gateway_base}${var.release_set[local.gateway_key].health_path}"
}

output "service_endpoints" {
  value = merge(
    { for k, s in azurerm_container_app.backend : k => "https://${s.ingress[0].fqdn}" },
    { for k, s in azurerm_container_app.public : k => "https://${s.ingress[0].fqdn}" },
  )
}

output "environment_name" {
  value = local.name
}
