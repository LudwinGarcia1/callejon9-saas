variable "location" {
  description = "Azure region for the frontend. The Azure for Students policy of this subscription only allows westus, eastus, eastus2, centralus and westus3."
  type        = string
  default     = "westus3"
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

variable "backend_origin" {
  description = "Public HTTPS origin of the API (api_url output of azure-backend), without path or trailing slash. Next.js proxies /api/v1/* to it from the server."
  type        = string

  validation {
    condition     = can(regex("^https://[a-z0-9.-]+(:[0-9]+)?$", var.backend_origin))
    error_message = "The backend origin must be a single https:// origin without path or trailing slash."
  }
}

variable "web_app_name" {
  description = "Web App name and therefore its public hostname, <name>.azurewebsites.net. It is global across Azure, so it must be free."
  type        = string
  default     = "restohub"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{0,58}[a-z0-9]$", var.web_app_name))
    error_message = "The name must be 2-60 lowercase letters, digits or hyphens, without a leading or trailing hyphen."
  }
}

variable "app_service_sku" {
  description = "App Service plan SKU. F1 is free but unloads the app when idle and caps CPU at 60 minutes a day; B1 keeps it loaded with Always On."
  type        = string
  default     = "F1"

  validation {
    condition     = contains(["F1", "B1", "B2", "B3"], var.app_service_sku)
    error_message = "The SKU must be F1, B1, B2 or B3."
  }
}

variable "monitoring_enabled" {
  description = "Creates Log Analytics and Application Insights with a 0.1 GB daily cap."
  type        = bool
  default     = false
}
