terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.8"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # Partial configuration: values come from backend.hcl (see backend.hcl.example).
  # The azurerm backend locks the state with a blob lease.
  backend "azurerm" {}
}

provider "azurerm" {
  features {
    key_vault {
      # The environment must be destroyable without leaving soft-deleted vaults behind.
      purge_soft_delete_on_destroy = true
    }
  }

  # azurerm 5.x registers no resource providers by default; a new subscription needs these.
  resource_providers_to_register = [
    "Microsoft.DBforPostgreSQL",
    "Microsoft.Insights",
    "Microsoft.KeyVault",
    "Microsoft.OperationalInsights",
    "Microsoft.Web",
  ]
}
