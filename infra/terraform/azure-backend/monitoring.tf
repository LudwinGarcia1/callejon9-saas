# Daily caps keep ingestion inside the monthly free allowance of Log Analytics.
resource "azurerm_log_analytics_workspace" "backend" {
  count = var.monitoring_enabled ? 1 : 0

  name                = "log-${local.name}-001"
  resource_group_name = azurerm_resource_group.backend.name
  location            = azurerm_resource_group.backend.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  daily_quota_gb      = 0.1
  tags                = local.tags
}

resource "azurerm_application_insights" "backend" {
  count = var.monitoring_enabled ? 1 : 0

  name                 = "appi-${local.name}-001"
  resource_group_name  = azurerm_resource_group.backend.name
  location             = azurerm_resource_group.backend.location
  workspace_id         = azurerm_log_analytics_workspace.backend[0].id
  application_type     = "java"
  daily_data_cap_in_gb = 0.1
  tags                 = local.tags
}

# Covers the whole subscription, not only this group: the student credit is shared.
resource "azurerm_consumption_budget_subscription" "credit" {
  count = length(var.budget_contact_emails) > 0 ? 1 : 0

  name            = "budget-${local.name}"
  subscription_id = "/subscriptions/${data.azurerm_client_config.current.subscription_id}"
  amount          = var.monthly_budget_usd
  time_grain      = "Monthly"

  time_period {
    start_date = formatdate("YYYY-MM-01'T'00:00:00Z", timestamp())
  }

  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThanOrEqualTo"
    threshold_type = "Actual"
    contact_emails = var.budget_contact_emails
  }

  notification {
    enabled        = true
    threshold      = 100
    operator       = "GreaterThanOrEqualTo"
    threshold_type = "Forecasted"
    contact_emails = var.budget_contact_emails
  }

  lifecycle {
    # The start date is fixed at creation; timestamp() would change it every plan.
    ignore_changes = [time_period]
  }
}
