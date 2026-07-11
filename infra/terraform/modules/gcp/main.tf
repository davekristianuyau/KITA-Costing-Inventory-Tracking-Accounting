# GCP module — Cloud Run service per Release-Set entry (public external / backend internal),
# Cloud SQL private IP, GCS, Secret Manager, Serverless VPC connector. Matches the module contract.

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
  name             = module.naming.name_prefix
  labels           = module.naming.tags
  region           = var.region != "" ? var.region : "us-central1"
  public_services  = { for k, v in var.release_set : k => v if v.visibility == "public" }
  private_services = { for k, v in var.release_set : k => v if v.visibility == "private" }
  image_ref        = { for k, v in var.release_set : k => "${v.image}:${v.version}" }
  gateway_key      = contains(keys(local.public_services), "gateway") ? "gateway" : keys(local.public_services)[0]
  # PROD never runs the "small" profile (FR-008a).
  effective_size = var.env == "prod" && var.size == "small" ? "standard" : var.size
}
