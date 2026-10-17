terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.8"
    }
  }

  # Partial configuration: values come from backend.hcl (see backend.hcl.example).
  # Same bootstrap as azure-backend, run in the frontend subscription.
  backend "azurerm" {}
}

provider "azurerm" {
  features {}

  # azurerm 5.x registers no resource providers by default; a new subscription needs these.
  resource_providers_to_register = [
    "Microsoft.Insights",
    "Microsoft.OperationalInsights",
    "Microsoft.Web",
  ]
}
