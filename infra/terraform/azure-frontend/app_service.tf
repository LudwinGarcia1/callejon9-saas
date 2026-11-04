resource "azurerm_service_plan" "frontend" {
  name                = "asp-${local.name}-001"
  resource_group_name = azurerm_resource_group.frontend.name
  location            = azurerm_resource_group.frontend.location
  os_type             = "Linux"
  sku_name            = var.app_service_sku
  worker_count        = 1
  tags                = local.tags
}

locals {
  free_tier = var.app_service_sku == "F1"

  monitoring_settings = var.monitoring_enabled ? {
    APPLICATIONINSIGHTS_CONNECTION_STRING = azurerm_application_insights.frontend[0].connection_string
  } : {}

  # Read at runtime by src/middleware.ts; no rebuild needed to set or rotate it.
  proxy_settings = var.backend_proxy_secret == "" ? {} : {
    BACKEND_PROXY_SECRET = trimspace(var.backend_proxy_secret)
  }
}

resource "azurerm_linux_web_app" "web" {
  # The name is the public hostname (<name>.azurewebsites.net). Changing it
  # replaces the Web App: the old hostname stops answering.
  name                = var.web_app_name
  resource_group_name = azurerm_resource_group.frontend.name
  location            = azurerm_resource_group.frontend.location
  service_plan_id     = azurerm_service_plan.frontend.id

  https_only              = true
  client_affinity_enabled = false

  # Deployments authenticate with Entra ID (az webapp deploy), never with publishing passwords.
  ftp_publish_basic_authentication_enabled       = false
  webdeploy_publish_basic_authentication_enabled = false

  site_config {
    # F1 does not allow Always On.
    always_on           = !local.free_tier
    minimum_tls_version = "1.2"
    ftps_state          = "Disabled"
    http2_enabled       = true

    # The package is the Next.js standalone output built by
    # scripts/deploy-frontend-azure.ps1. App Service sets HOSTNAME to the
    # container name and server.js binds to it, so it is forced here.
    app_command_line = "HOSTNAME=0.0.0.0 node server.js"

    # Eviction needs a second instance, which F1 does not have.
    health_check_path                 = local.free_tier ? null : "/login"
    health_check_eviction_time_in_min = local.free_tier ? null : 10

    application_stack {
      node_version = "24-lts"
    }
  }

  app_settings = merge({
    # The package is already built; Oryx must not run npm install or a build.
    SCM_DO_BUILD_DURING_DEPLOYMENT = "false"
    ENABLE_ORYX_BUILD              = "false"

    NODE_ENV                = "production"
    NEXT_TELEMETRY_DISABLED = "1"

    # The /api/v1/* rewrite is fixed at build time; this runtime value is what
    # the server-side fetches of src/lib/api.ts use. Both must point to the same API.
    BACKEND_ORIGIN = var.backend_origin
  }, local.monitoring_settings, local.proxy_settings)

  logs {
    detailed_error_messages = false
    failed_request_tracing  = false

    application_logs {
      file_system_level = "Warning"
    }

    http_logs {
      file_system {
        retention_in_days = 3
        retention_in_mb   = 35
      }
    }
  }

  tags = local.tags
}
