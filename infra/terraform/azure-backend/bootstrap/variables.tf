variable "location" {
  description = "Azure region for the Terraform state account."
  type        = string
  default     = "mexicocentral"
}

variable "state_admin_object_id" {
  description = "Entra ID object id of the operator who runs Terraform (az ad signed-in-user show --query id -o tsv)."
  type        = string
}
