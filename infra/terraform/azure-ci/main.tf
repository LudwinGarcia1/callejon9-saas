data "azurerm_client_config" "current" {}

locals {
  name = "restohub-${var.environment}"

  # Key Vault names are global; a hash of the subscription keeps them unique and stable.
  suffix = substr(sha1(data.azurerm_client_config.current.subscription_id), 0, 6)

  tags = {
    project     = "restohub"
    component   = "ci"
    environment = var.environment
    issue       = "CAL-48"
    managed_by  = "terraform"
  }
}

# Deletion is refused while the group holds resources Terraform does not manage,
# which makes orphaned resources visible on destroy.
resource "azurerm_resource_group" "ci" {
  name     = "rg-${local.name}"
  location = var.location
  tags     = local.tags
}
