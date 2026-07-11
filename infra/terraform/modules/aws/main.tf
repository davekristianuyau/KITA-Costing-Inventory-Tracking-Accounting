# AWS module — provisions the KITA service set (Release Set) on ECS Fargate behind a public ALB,
# with private backend services (Cloud Map), RDS PostgreSQL, S3, and Secrets Manager.
# Inputs/outputs match contracts/module-interface.md.

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

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = module.naming.name_prefix
  tags = module.naming.tags
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)

  public_services  = { for k, v in var.release_set : k => v if v.visibility == "public" }
  private_services = { for k, v in var.release_set : k => v if v.visibility == "private" }
  image_ref        = { for k, v in var.release_set : k => "${v.image}:${v.version}" }

  sizing = {
    small    = { cpu = 256, memory = 512 }
    standard = { cpu = 512, memory = 1024 }
    large    = { cpu = 1024, memory = 2048 }
  }
  # PROD never runs the "small" profile — same architecture, right-sized per env (FR-008a).
  effective_size = var.env == "prod" && var.size == "small" ? "standard" : var.size
  cpu            = local.sizing[local.effective_size].cpu
  memory         = local.sizing[local.effective_size].memory

  # The gateway is the public entry; prefer a service literally named "gateway", else any public one.
  gateway_key = contains(keys(local.public_services), "gateway") ? "gateway" : keys(local.public_services)[0]
  use_tls     = var.custom_domain != ""
}
