# AWS module — provisions the KITA service set (Release Set) on ECS Fargate behind a public ALB,
# with private backend services (Cloud Map), RDS PostgreSQL, S3, and Secrets Manager.
#
# STATUS: interface stub. Inputs/outputs match contracts/module-interface.md so the root config
# validates and providers switch by config today. Resource bodies (network/db/storage/compute/
# ingress/health) are implemented in the next increment (tasks T019–T027).

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

locals {
  # Backend services are private; only public services front the gateway/ALB.
  public_services  = { for k, v in var.release_set : k => v if v.visibility == "public" }
  private_services = { for k, v in var.release_set : k => v if v.visibility == "private" }
}

# Placeholders until resources are implemented; keep the module-interface outputs stable.
output "gateway_url" { value = "" }
output "aggregate_health_url" { value = "" }
output "service_endpoints" { value = { for k, v in var.release_set : k => "" } }
output "environment_name" { value = module.naming.name_prefix }
