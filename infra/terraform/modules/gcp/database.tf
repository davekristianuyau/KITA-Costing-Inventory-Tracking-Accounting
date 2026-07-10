# Cloud SQL PostgreSQL (private IP). PROD enables PITR.

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "google_sql_database_instance" "main" {
  name                = "${local.name}-db"
  database_version    = "POSTGRES_16"
  region              = local.region
  deletion_protection = var.env == "prod"

  settings {
    tier = var.size == "small" ? "db-f1-micro" : "db-custom-2-4096"
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
    }
    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = var.env == "prod"
    }
  }

  depends_on = [google_service_networking_connection.psa]
}

resource "google_sql_database" "kita" {
  name     = "kita"
  instance = google_sql_database_instance.main.name
}

resource "google_sql_user" "kita" {
  name     = "kita"
  instance = google_sql_database_instance.main.name
  password = random_password.db.result
}

resource "google_secret_manager_secret" "db_password" {
  secret_id = "${local.name}-db-password"
  labels    = local.labels
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}
