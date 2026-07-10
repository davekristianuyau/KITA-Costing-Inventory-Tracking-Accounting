# Cloud Run v2 service per Release-Set entry. Public services get external ingress + public invoke;
# backend services get internal-only ingress. Backend URLs are injected into public services.

locals {
  db_url            = "jdbc:postgresql://${google_sql_database_instance.main.private_ip_address}:5432/kita"
  backend_url_env   = { for k, s in google_cloud_run_v2_service.backend : "${upper(replace(k, "-", "_"))}_URL" => s.uri }
  base_value_env    = { DATABASE_URL = local.db_url, DATABASE_USER = "kita" }
  public_value_env  = merge(local.base_value_env, local.backend_url_env)
  private_value_env = local.base_value_env
}

resource "google_cloud_run_v2_service" "backend" {
  for_each = local.private_services
  name     = each.key
  location = local.region
  ingress  = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  labels   = local.labels

  template {
    vpc_access {
      connector = google_vpc_access_connector.connector.id
      egress    = "ALL_TRAFFIC"
    }
    containers {
      image = local.image_ref[each.key]
      ports { container_port = each.value.port }
      dynamic "env" {
        for_each = local.private_value_env
        content {
          name  = env.key
          value = env.value
        }
      }
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

resource "google_cloud_run_v2_service" "public" {
  for_each = local.public_services
  name     = each.key
  location = local.region
  ingress  = "INGRESS_TRAFFIC_ALL"
  labels   = local.labels

  template {
    vpc_access {
      connector = google_vpc_access_connector.connector.id
      egress    = "ALL_TRAFFIC"
    }
    containers {
      image = local.image_ref[each.key]
      ports { container_port = each.value.port }
      dynamic "env" {
        for_each = local.public_value_env
        content {
          name  = env.key
          value = env.value
        }
      }
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

# Public services are invokable from the internet; backend services are not.
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  for_each = local.public_services
  location = local.region
  name     = google_cloud_run_v2_service.public[each.key].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
