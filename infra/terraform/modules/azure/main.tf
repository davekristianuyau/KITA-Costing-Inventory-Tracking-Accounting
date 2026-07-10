# Azure module — Container Apps (external/internal ingress per visibility), Azure DB for PostgreSQL
# Flexible (private), Blob storage, Key Vault. Matches the module contract.

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

data "azurerm_client_config" "current" {}

locals {
  name             = module.naming.name_prefix
  tags             = module.naming.tags
  location         = var.region != "" ? var.region : "eastus"
  public_services  = { for k, v in var.release_set : k => v if v.visibility == "public" }
  private_services = { for k, v in var.release_set : k => v if v.visibility == "private" }
  image_ref        = { for k, v in var.release_set : k => "${v.image}:${v.version}" }
  gateway_key      = contains(keys(local.public_services), "gateway") ? "gateway" : keys(local.public_services)[0]

  sizing       = { small = { cpu = 0.5, memory = "1Gi" }, standard = { cpu = 1.0, memory = "2Gi" }, large = { cpu = 2.0, memory = "4Gi" } }
  cpu          = local.sizing[var.size].cpu
  memory       = local.sizing[var.size].memory
  sa_name      = substr(lower(replace("${var.client_name}${var.env}sa", "-", "")), 0, 24)
  kv_name      = substr(lower(replace("${var.client_name}${var.env}kv", "-", "")), 0, 24)
  backend_urls = { for k, v in local.private_services : "${upper(replace(k, "-", "_"))}_URL" => "https://${k}.internal.${azurerm_container_app_environment.main.default_domain}" }
}
