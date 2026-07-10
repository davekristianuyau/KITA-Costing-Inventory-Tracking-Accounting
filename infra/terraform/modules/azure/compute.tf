# Log Analytics + Container Apps environment (VNet-integrated). Public services get external ingress;
# backend services get internal ingress. Backend URLs are injected into public services.

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.name}-logs"
  resource_group_name = azurerm_resource_group.main.name
  location            = local.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "main" {
  name                       = "${local.name}-cae"
  resource_group_name        = azurerm_resource_group.main.name
  location                   = local.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  infrastructure_subnet_id   = azurerm_subnet.apps.id
  tags                       = local.tags
}

locals {
  db_url         = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/kita"
  base_env       = { DATABASE_URL = local.db_url, DATABASE_USER = "kita" }
  public_env_map = merge(local.base_env, local.backend_urls)
}

resource "azurerm_container_app" "backend" {
  for_each                     = local.private_services
  name                         = each.key
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  tags                         = local.tags

  secret {
    name  = "db-password"
    value = random_password.db.result
  }

  ingress {
    external_enabled = false
    target_port      = each.value.port
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }

  template {
    container {
      name   = each.key
      image  = local.image_ref[each.key]
      cpu    = local.cpu
      memory = local.memory

      dynamic "env" {
        for_each = local.base_env
        content {
          name  = env.key
          value = env.value
        }
      }
      env {
        name        = "DATABASE_PASSWORD"
        secret_name = "db-password"
      }
    }
  }
}

resource "azurerm_container_app" "public" {
  for_each                     = local.public_services
  name                         = each.key
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  tags                         = local.tags

  secret {
    name  = "db-password"
    value = random_password.db.result
  }

  ingress {
    external_enabled = true
    target_port      = each.value.port
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }

  template {
    container {
      name   = each.key
      image  = local.image_ref[each.key]
      cpu    = local.cpu
      memory = local.memory

      dynamic "env" {
        for_each = local.public_env_map
        content {
          name  = env.key
          value = env.value
        }
      }
      env {
        name        = "DATABASE_PASSWORD"
        secret_name = "db-password"
      }
    }
  }
}
