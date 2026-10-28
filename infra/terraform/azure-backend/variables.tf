variable "location" {
  description = "Azure region for the backend."
  type        = string
  default     = "mexicocentral"
}

variable "environment" {
  description = "Short environment name used in resource names and tags."
  type        = string
  default     = "prod"

  validation {
    condition     = contains(["dev", "qa", "prod"], var.environment)
    error_message = "The environment must be one of: dev, qa, prod."
  }
}

variable "frontend_origin" {
  description = "Public HTTPS origin of the frontend, without path or trailing slash (e.g. https://callejon9.vercel.app). Becomes APP_ALLOWED_ORIGINS."
  type        = string

  validation {
    condition     = can(regex("^https://[a-z0-9.-]+(:[0-9]+)?$", var.frontend_origin))
    error_message = "The frontend origin must be a single https:// origin without path, wildcard or trailing slash."
  }
}

variable "trusted_proxy_addresses" {
  description = "Outbound IPs (or CIDR ranges) of the frontend web app. ClientIp skips them in X-Forwarded-For so the per-IP login and signup limits key on the browser, not on the frontend. Becomes APP_TRUSTED_PROXIES. Take them from the outbound_ip_addresses output of azure-frontend."
  type        = list(string)
  default     = []

  validation {
    condition = alltrue([
      for address in var.trusted_proxy_addresses :
      can(cidrhost(address, 0)) || can(cidrhost("${address}/32", 0)) || can(cidrhost("${address}/128", 0))
    ])
    error_message = "Each trusted proxy must be an IP address or a CIDR range."
  }
}

variable "app_service_sku" {
  description = "App Service plan SKU. B1 is the cheapest tier with Always On; F1 unloads the app and caps CPU at 60 minutes a day."
  type        = string
  default     = "B1"
}

variable "postgres_version" {
  description = "PostgreSQL major version. The backend tests run against PostgreSQL 16."
  type        = string
  default     = "16"
}

variable "postgres_sku" {
  description = "PostgreSQL Flexible Server SKU. B_Standard_B1ms is covered by the 12-month free services of new Azure accounts."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  description = "PostgreSQL storage in MB. 32768 is the smallest size and the one covered by the free services."
  type        = number
  default     = 32768
}

variable "postgres_admin_login" {
  description = "Administrator login of the PostgreSQL server. Only used for the one-time role bootstrap; the backend never connects with it."
  type        = string
  default     = "c9admin"

  validation {
    condition     = !contains(["azure_superuser", "azure_pg_admin", "admin", "administrator", "root", "guest", "public"], var.postgres_admin_login) && !startswith(var.postgres_admin_login, "pg_")
    error_message = "The administrator login cannot be a reserved PostgreSQL or Azure name."
  }
}

variable "operator_ip_address" {
  description = "Public IPv4 of the operator, allowed through the PostgreSQL firewall only while running scripts/setup-db-azure.sql. Leave null afterwards."
  type        = string
  default     = null

  validation {
    condition     = var.operator_ip_address == null || can(cidrhost("${var.operator_ip_address}/32", 0))
    error_message = "The operator IP must be a single IPv4 address."
  }
}

variable "key_vault_admin_object_id" {
  description = "Entra ID object id allowed to manage Key Vault secrets (az ad signed-in-user show --query id -o tsv)."
  type        = string
}

variable "monitoring_enabled" {
  description = "Creates Log Analytics and Application Insights with a 0.1 GB daily cap."
  type        = bool
  default     = true
}

variable "swagger_enabled" {
  description = "Serves OpenAPI (/v3/api-docs) and Swagger UI (/swagger-ui.html) from the API. The prod profile turns them off; this overrides it for the team's frontend work."
  type        = bool
  default     = false
}

variable "monthly_budget_usd" {
  description = "Monthly subscription budget that triggers e-mail alerts."
  type        = number
  default     = 20
}

variable "budget_contact_emails" {
  description = "E-mail addresses for budget alerts. Empty disables the budget."
  type        = list(string)
  default     = []
}
