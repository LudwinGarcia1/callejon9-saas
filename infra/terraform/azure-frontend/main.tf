data "azurerm_client_config" "current" {}

locals {
  region_short = lookup({
    westus3   = "wus3"
    westus    = "wus"
    eastus    = "eus"
    eastus2   = "eus2"
    centralus = "cus"
  }, var.location, var.location)

  name = "callejon9-web-${var.environment}-${local.region_short}"

  # The web app name is a global DNS name; a hash of the subscription keeps it
  # unique and stable, as in azure-backend.
  unique = substr(sha1(data.azurerm_client_config.current.subscription_id), 0, 6)

  tags = {
    project     = "callejon9"
    component   = "frontend"
    environment = var.environment
    managed_by  = "terraform"
  }
}

resource "azurerm_resource_group" "frontend" {
  name     = "rg-${local.name}-001"
  location = var.location
  tags     = local.tags
}
