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
}
