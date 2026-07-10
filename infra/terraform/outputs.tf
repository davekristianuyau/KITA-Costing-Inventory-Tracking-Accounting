output "gateway_url" {
  description = "Public HTTPS URL of the gateway (single entry point)."
  value       = one(concat(module.aws[*].gateway_url, module.gcp[*].gateway_url, module.azure[*].gateway_url))
}

output "aggregate_health_url" {
  value = one(concat(module.aws[*].aggregate_health_url, module.gcp[*].aggregate_health_url, module.azure[*].aggregate_health_url))
}

output "service_endpoints" {
  value = one(concat(module.aws[*].service_endpoints, module.gcp[*].service_endpoints, module.azure[*].service_endpoints))
}

output "environment_name" {
  value = one(concat(module.aws[*].environment_name, module.gcp[*].environment_name, module.azure[*].environment_name))
}
