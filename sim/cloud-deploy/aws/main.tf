# 010 — AWS wrapper root: point the aws provider at Floci and deploy the REAL 001 AWS module (emulated=true).
# No real cloud. Terraform-native verify (state list) + destroy is driven by deploy-check.sh.

terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
}

variable "floci_endpoint" {
  type    = string
  default = "http://floci:4566"
}

# Deploy-check uses true (skip the slow managed DB); set false to plan the real-cloud path (FR-005 invariant).
variable "emulated" {
  type    = bool
  default = true
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  s3_use_path_style           = true

  endpoints {
    ec2              = var.floci_endpoint
    ecs              = var.floci_endpoint
    rds              = var.floci_endpoint
    elbv2            = var.floci_endpoint
    acm              = var.floci_endpoint
    servicediscovery = var.floci_endpoint
    logs             = var.floci_endpoint
    iam              = var.floci_endpoint
    sts              = var.floci_endpoint
    secretsmanager   = var.floci_endpoint
    s3               = var.floci_endpoint
  }
}

module "aws" {
  source                   = "../../../infra/terraform/modules/aws"
  emulated                 = var.emulated
  client_name              = "probe"
  env                      = "stg"
  region                   = "us-east-1"
  size                     = "small"
  custom_domain            = ""
  db_backup_retention_days = 1
  tags                     = { imitation = "floci" }
  release_set = {
    gateway = {
      image       = "kita/gateway"
      version     = "0.0.1"
      visibility  = "public"
      port        = 8081
      health_path = "/actuator/health"
    }
    operations = {
      image       = "kita/operations-service"
      version     = "0.0.1"
      visibility  = "private"
      port        = 8083
      health_path = "/actuator/health"
    }
  }
}
