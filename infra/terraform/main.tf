# Root: select exactly one per-cloud module by var.cloud_provider. Each module implements the
# same interface (contracts/module-interface.md), so switching providers is config-only.

provider "aws" {
  region = var.region != "" ? var.region : "us-east-1"
}

provider "google" {
  project = var.gcp_project != "" ? var.gcp_project : null
  region  = var.region != "" ? var.region : "us-central1"
}

provider "azurerm" {
  features {}
}

module "aws" {
  source                   = "./modules/aws"
  count                    = var.cloud_provider == "aws" ? 1 : 0
  client_name              = var.client_name
  env                      = var.env
  region                   = var.region != "" ? var.region : "us-east-1"
  release_set              = var.release_set
  size                     = var.size
  custom_domain            = var.custom_domain
  db_backup_retention_days = var.db_backup_retention_days
  tags                     = var.tags
}

module "gcp" {
  source                   = "./modules/gcp"
  count                    = var.cloud_provider == "gcp" ? 1 : 0
  client_name              = var.client_name
  env                      = var.env
  region                   = var.region
  release_set              = var.release_set
  size                     = var.size
  custom_domain            = var.custom_domain
  db_backup_retention_days = var.db_backup_retention_days
  tags                     = var.tags
}

module "azure" {
  source                   = "./modules/azure"
  count                    = var.cloud_provider == "azure" ? 1 : 0
  client_name              = var.client_name
  env                      = var.env
  region                   = var.region
  release_set              = var.release_set
  size                     = var.size
  custom_domain            = var.custom_domain
  db_backup_retention_days = var.db_backup_retention_days
  tags                     = var.tags
}
