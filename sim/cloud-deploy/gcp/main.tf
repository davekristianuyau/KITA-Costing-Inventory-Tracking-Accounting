# 010 — GCP connectivity spike (C2). Proves the Terraform `google` provider CAN target Floci-GCP via
# per-service *_custom_endpoint + a dummy OAuth token, and exercises the Floci-GCP-SUPPORTED services
# (Cloud Storage + Secret Manager). Floci-GCP does NOT implement the Compute API (network create → HTTP 405),
# so the full 001 GCP module (VPC/Cloud SQL/VPC-connector/Cloud Run) cannot deploy here — see coverage/gcp.md.
# No real cloud; dummy creds via GOOGLE_OAUTH_ACCESS_TOKEN.

terraform {
  required_version = ">= 1.9"
  required_providers {
    google = { source = "hashicorp/google", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
}

variable "floci" {
  type    = string
  default = "http://floci-gcp:4588"
}

provider "google" {
  project                        = "floci-local"
  region                         = "us-central1"
  storage_custom_endpoint        = "${var.floci}/storage/v1/"
  secret_manager_custom_endpoint = "${var.floci}/v1/"
}

resource "google_storage_bucket" "main" {
  name          = "probe-stg-storage"
  location      = "US"
  force_destroy = true
}

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "google_secret_manager_secret" "db_password" {
  secret_id = "probe-stg-db-password"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}
