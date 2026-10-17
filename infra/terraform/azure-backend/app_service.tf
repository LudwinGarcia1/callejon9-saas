resource "azurerm_service_plan" "backend" {
  name                = "asp-${local.name}-001"
  resource_group_name = azurerm_resource_group.backend.name
  location            = azurerm_resource_group.backend.location
  os_type             = "Linux"
  sku_name            = var.app_service_sku

  # One instance on purpose: the login and signup rate limits and the WebSocket
  # connection registry live in memory. A second instance would split them.
  worker_count = 1

  tags = local.tags
}

locals {
  key_vault_reference = {
    for name, secret in azurerm_key_vault_secret.backend :
    name => "@Microsoft.KeyVault(SecretUri=${secret.versionless_id})"
  }

  monitoring_settings = var.monitoring_enabled ? {
    APPLICATIONINSIGHTS_CONNECTION_STRING      = azurerm_application_insights.backend[0].connection_string
    ApplicationInsightsAgent_EXTENSION_VERSION = "~3"
  } : {}

  # Environment variables take precedence over application-prod.yml.
  swagger_settings = var.swagger_enabled ? {
    SPRINGDOC_APIDOCS_ENABLED   = "true"
    SPRINGDOC_SWAGGERUI_ENABLED = "true"
  } : {}
}

resource "azurerm_linux_web_app" "api" {
  name                = "app-callejon9-api-${var.environment}-${local.unique}"
  resource_group_name = azurerm_resource_group.backend.name
  location            = azurerm_resource_group.backend.location
  service_plan_id     = azurerm_service_plan.backend.id

  https_only              = true
  client_affinity_enabled = false

  # Deployments authenticate with Entra ID (az webapp deploy), never with publishing passwords.
  ftp_publish_basic_authentication_enabled       = false
  webdeploy_publish_basic_authentication_enabled = false

  identity {
    type = "SystemAssigned"
  }

  site_config {
    # Flyway and Spring Boot take tens of seconds to start; an unloaded app would
    # repeat that on the first request after every idle period.
    always_on           = true
    minimum_tls_version = "1.2"
    ftps_state          = "Disabled"
    http2_enabled       = true
    websockets_enabled  = true

    # Liveness on purpose (docs/operacion-probes.md): App Service replaces an
    # unhealthy instance, and a PostgreSQL outage must not trigger restarts that
    # fix nothing. /actuator/health and the readiness group include the database.
    health_check_path                 = "/actuator/health/liveness"
    health_check_eviction_time_in_min = 10

    application_stack {
      java_server         = "JAVA"
      java_server_version = "21"
      java_version        = "21"
    }
  }

  app_settings = merge({
    SPRING_PROFILES_ACTIVE = "prod"
    SERVER_PORT            = "80"
    JAVA_OPTS              = "-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

    # Overrides the localhost URL of application.yml; Flyway reuses it. The server
    # rejects connections without TLS (require_secure_transport defaults to ON).
    SPRING_DATASOURCE_URL = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.app.name}?sslmode=require"

    DB_APP_PASSWORD   = local.key_vault_reference["db-app-password"]
    DB_OWNER_PASSWORD = local.key_vault_reference["db-owner-password"]
    JWT_SECRET        = local.key_vault_reference["jwt-secret"]

    # Ignored while the prod profile is active: it pins Secure cookies. Kept so
    # the cookies stay secure if the profile is ever changed.
    AUTH_COOKIE_SECURE  = "true"
    APP_ALLOWED_ORIGINS = var.frontend_origin
  }, local.monitoring_settings, local.swagger_settings)

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
