terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.8"
    }
  }

  # Partial configuration: values come from backend.hcl (see backend.hcl.example).
  # The azurerm backend locks the state with a blob lease.
  backend "azurerm" {}
}

provider "azurerm" {
  features {
    key_vault {
      # A test environment must be destroyable without leaving soft-deleted vaults behind.
      purge_soft_delete_on_destroy = true
    }
  }
}
