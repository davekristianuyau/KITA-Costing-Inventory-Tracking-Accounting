resource "random_password" "db" {
  length  = 24
  special = false
}

resource "azurerm_postgresql_flexible_server" "main" {
  name                          = "${local.name}-pg"
  resource_group_name           = azurerm_resource_group.main.name
  location                      = local.location
  version                       = "16"
  delegated_subnet_id           = azurerm_subnet.db.id
  private_dns_zone_id           = azurerm_private_dns_zone.pg.id
  administrator_login           = "kita"
  administrator_password        = random_password.db.result
  sku_name                      = local.effective_size == "small" ? "B_Standard_B1ms" : "GP_Standard_D2s_v3"
  storage_mb                    = 32768
  backup_retention_days         = var.env == "prod" ? max(var.db_backup_retention_days, 7) : var.db_backup_retention_days
  public_network_access_enabled = false
  zone                          = "1"
  tags                          = local.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.pg]
}

resource "azurerm_postgresql_flexible_server_database" "kita" {
  name      = "kita"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_key_vault" "main" {
  name                       = local.kv_name
  resource_group_name        = azurerm_resource_group.main.name
  location                   = local.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  tags                       = local.tags

  access_policy {
    tenant_id          = data.azurerm_client_config.current.tenant_id
    object_id          = data.azurerm_client_config.current.object_id
    secret_permissions = ["Get", "List", "Set", "Delete", "Purge"]
  }
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "db-password"
  value        = random_password.db.result
  key_vault_id = azurerm_key_vault.main.id
}
