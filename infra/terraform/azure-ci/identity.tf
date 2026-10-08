resource "azurerm_user_assigned_identity" "jenkins" {
  name                = "id-jenkins"
  resource_group_name = azurerm_resource_group.ci.name
  location            = azurerm_resource_group.ci.location
  tags                = local.tags
}

resource "azurerm_key_vault" "ci" {
  name                       = "kv-rh-${var.environment}-${local.suffix}"
  resource_group_name        = azurerm_resource_group.ci.name
  location                   = azurerm_resource_group.ci.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  soft_delete_retention_days = 7
  purge_protection_enabled   = false
  tags                       = local.tags
}

# The Jenkins identity can only read secrets from this vault; anything else is denied.
resource "azurerm_role_assignment" "jenkins_secrets_reader" {
  scope                = azurerm_key_vault.ci.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.jenkins.principal_id
  principal_type       = "ServicePrincipal"
}

resource "azurerm_role_assignment" "key_vault_admin" {
  scope                = azurerm_key_vault.ci.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = var.key_vault_admin_object_id
}
