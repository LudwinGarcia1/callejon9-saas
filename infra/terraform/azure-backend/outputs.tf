output "api_url" {
  description = "Public HTTPS origin of the API. The frontend uses it as BACKEND_ORIGIN."
  value       = "https://${azurerm_linux_web_app.api.default_hostname}"
}

output "resource_group_name" {
  value = azurerm_resource_group.backend.name
}

output "web_app_name" {
  description = "Target of az webapp deploy."
  value       = azurerm_linux_web_app.api.name
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_admin_login" {
  value = azurerm_postgresql_flexible_server.main.administrator_login
}

output "key_vault_name" {
  description = "Holds jwt-secret, db-app-password, db-owner-password and postgres-admin-password."
  value       = azurerm_key_vault.backend.name
}

output "app_outbound_ip_addresses" {
  description = "Addresses allowed through the PostgreSQL firewall."
  value       = azurerm_linux_web_app.api.possible_outbound_ip_address_list
}
