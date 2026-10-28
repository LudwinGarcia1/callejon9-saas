output "frontend_url" {
  description = "Public HTTPS origin of the frontend. azure-backend needs it as frontend_origin."
  value       = "https://${azurerm_linux_web_app.web.default_hostname}"
}

output "outbound_ip_addresses" {
  description = "Addresses the frontend uses to call the API. azure-backend needs them as trusted_proxy_addresses."
  value       = azurerm_linux_web_app.web.possible_outbound_ip_address_list
}

output "resource_group_name" {
  value = azurerm_resource_group.frontend.name
}

output "web_app_name" {
  description = "Target of az webapp deploy."
  value       = azurerm_linux_web_app.web.name
}
