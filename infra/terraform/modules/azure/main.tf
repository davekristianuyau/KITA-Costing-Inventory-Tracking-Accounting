# Azure module — Container Apps environment (external/internal ingress per visibility), Azure DB
# for PostgreSQL Flexible (private), Blob, Key Vault, managed cert.
#
# STATUS: interface stub (task T045). Inputs/outputs match contracts/module-interface.md.

variable "client_name" { type = string }
variable "env" { type = string }
variable "region" { type = string }
variable "release_set" {
  type = map(object({
    image       = string
    version     = string
    visibility  = string
    port        = number
    health_path = string
  }))
}
variable "size" { type = string }
variable "custom_domain" { type = string }
variable "db_backup_retention_days" { type = number }
variable "tags" { type = map(string) }

module "naming" {
  source      = "../common"
  client_name = var.client_name
  env         = var.env
  tags        = var.tags
}

output "gateway_url" { value = "" }
output "aggregate_health_url" { value = "" }
output "service_endpoints" { value = { for k, v in var.release_set : k => "" } }
output "environment_name" { value = module.naming.name_prefix }
