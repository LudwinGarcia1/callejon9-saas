resource "azurerm_postgresql_flexible_server" "main" {
  name                = "psql-callejon9-${var.environment}-${local.unique}"
  resource_group_name = azurerm_resource_group.backend.name
  location            = azurerm_resource_group.backend.location

  version      = var.postgres_version
  sku_name     = var.postgres_sku
  storage_mb   = var.postgres_storage_mb
  storage_tier = "P4"

  # Growing storage silently would leave the free allowance without warning.
  auto_grow_enabled = false

  backup_retention_days        = 7
  geo_redundant_backup_enabled = false

  # Public endpoint restricted by the firewall rules below. Private access would
  # need VNet integration and would lock the operator out of the role bootstrap.
  public_network_access_enabled = true

  administrator_login    = var.postgres_admin_login
  administrator_password = random_password.postgres_admin.result

  authentication {
    password_auth_enabled         = true
    active_directory_auth_enabled = false
  }

  tags = local.tags

  lifecycle {
    # Azure picks the zone and changes it after a failover.
    ignore_changes = [zone]
  }
}

# V1__control_plane.sql runs CREATE EXTENSION pgcrypto; Flexible Server only
# allows extensions listed here.
resource "azurerm_postgresql_flexible_server_configuration" "extensions" {
  name      = "azure.extensions"
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = "PGCRYPTO"
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  name      = "callejon9"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# Only the web app's possible outbound addresses reach the database. Those
# addresses are known after the web app exists, so the first apply runs in two
# steps (see README.md).
resource "azurerm_postgresql_flexible_server_firewall_rule" "app_outbound" {
  for_each = toset(azurerm_linux_web_app.api.possible_outbound_ip_address_list)

  name             = "app-outbound-${replace(each.value, ".", "-")}"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = each.value
  end_ip_address   = each.value
}

# Temporary access for scripts/setup-db-azure.sql. Remove the variable when done.
resource "azurerm_postgresql_flexible_server_firewall_rule" "operator" {
  count = var.operator_ip_address == null ? 0 : 1

  name             = "operator-temporary"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = var.operator_ip_address
  end_ip_address   = var.operator_ip_address
}
