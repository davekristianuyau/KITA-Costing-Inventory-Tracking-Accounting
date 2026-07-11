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
    tier = local.effective_size == "small" ? "db-f1-micro" : "db-custom-2-4096"
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
    }
    # PROD: daily backups + PITR with 7-day transaction-log retention.
    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = var.env == "prod"
      transaction_log_retention_days = var.env == "prod" ? 7 : 1
      backup_retention_settings {
        retained_backups = var.env == "prod" ? max(var.db_backup_retention_days, 7) : var.db_backup_retention_days
      }
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
