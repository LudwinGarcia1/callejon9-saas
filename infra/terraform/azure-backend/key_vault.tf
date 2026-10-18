# The generated values live in the Terraform state as well, which is why the
# state sits in a private storage account with Entra ID access only.
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "db_app" {
  length  = 32
  special = false
}

resource "random_password" "db_owner" {
  length  = 32
  special = false
}

# Azure requires characters from three of four categories.
resource "random_password" "postgres_admin" {
  length      = 32
  special     = false
  min_upper   = 2
  min_lower   = 2
  min_numeric = 2
}

resource "azurerm_key_vault" "backend" {
  name                       = "kv-c9-${var.environment}-${local.unique}"
  resource_group_name        = azurerm_resource_group.backend.name
  location                   = azurerm_resource_group.backend.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  purge_protection_enabled   = false
  tags                       = local.tags
}

resource "azurerm_role_assignment" "key_vault_admin" {
  scope                = azurerm_key_vault.backend.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = var.key_vault_admin_object_id
}

# The web app can only read secrets; it resolves the @Microsoft.KeyVault references
# of its app settings with its system-assigned identity.
resource "azurerm_role_assignment" "api_secrets_reader" {
  scope                = azurerm_key_vault.backend.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_web_app.api.identity[0].principal_id
  principal_type       = "ServicePrincipal"
}

locals {
  secret_values = {
    "jwt-secret"              = random_password.jwt.result
    "db-app-password"         = random_password.db_app.result
    "db-owner-password"       = random_password.db_owner.result
    "postgres-admin-password" = random_password.postgres_admin.result
  }
}

resource "azurerm_key_vault_secret" "backend" {
  # Iterates over names only: sensitive values cannot be for_each keys.
  for_each = toset(keys(local.secret_values))

  name         = each.key
  value        = local.secret_values[each.key]
  key_vault_id = azurerm_key_vault.backend.id
  content_type = "text/plain"

  # RBAC role assignments can take a minute to propagate; re-run apply on a 403.
  depends_on = [azurerm_role_assignment.key_vault_admin]
}
