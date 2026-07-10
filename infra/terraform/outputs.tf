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

output "db_connection_secret_ref" {
  description = "Reference (not value) to the DB credentials in the cloud secret store."
  value       = one(concat(module.aws[*].db_connection_secret_ref, module.gcp[*].db_connection_secret_ref, module.azure[*].db_connection_secret_ref))
}

output "object_storage_ref" {
  value = one(concat(module.aws[*].object_storage_ref, module.gcp[*].object_storage_ref, module.azure[*].object_storage_ref))
}

output "resource_ids" {
  description = "Logical name → provider resource id (audit/teardown)."
  value       = one(concat(module.aws[*].resource_ids, module.gcp[*].resource_ids, module.azure[*].resource_ids))
}
