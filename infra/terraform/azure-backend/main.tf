data "azurerm_client_config" "current" {}

locals {
  region_short = lookup({
    mexicocentral = "mxc"
    eastus        = "eus"
    centralus     = "cus"
    westus2       = "wus2"
    canadacentral = "cac"
  }, var.location, var.location)

  name = "callejon9-${var.environment}-${local.region_short}"

  # Web app, PostgreSQL and Key Vault names are global DNS names; a hash of the
  # subscription keeps them unique and stable.
  unique = substr(sha1(data.azurerm_client_config.current.subscription_id), 0, 6)

  tags = {
    project     = "callejon9"
    component   = "backend"
    environment = var.environment
    managed_by  = "terraform"
  }
}

# Deletion is refused while the group holds resources Terraform does not manage,
# which makes orphaned resources visible on destroy.
resource "azurerm_resource_group" "backend" {
  name     = "rg-${local.name}-001"
  location = var.location
  tags     = local.tags
}
