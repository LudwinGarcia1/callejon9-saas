terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.8"
    }
  }
}

provider "azurerm" {
  features {}

  # The state account disables shared keys, so data-plane calls must use Entra ID.
  storage_use_azuread = true

  # azurerm 5.x registers no resource providers by default; a new subscription needs this one.
  resource_providers_to_register = ["Microsoft.Storage"]
}
