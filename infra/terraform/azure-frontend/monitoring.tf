resource "azurerm_log_analytics_workspace" "frontend" {
  count = var.monitoring_enabled ? 1 : 0

  name                = "log-${local.name}-001"
  resource_group_name = azurerm_resource_group.frontend.name
  location            = azurerm_resource_group.frontend.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  daily_quota_gb      = 0.1
  tags                = local.tags
}

resource "azurerm_application_insights" "frontend" {
  count = var.monitoring_enabled ? 1 : 0

  name                 = "appi-${local.name}-001"
  resource_group_name  = azurerm_resource_group.frontend.name
  location             = azurerm_resource_group.frontend.location
  workspace_id         = azurerm_log_analytics_workspace.frontend[0].id
  application_type     = "Node.JS"
  daily_data_cap_in_gb = 0.1
  tags                 = local.tags
}
