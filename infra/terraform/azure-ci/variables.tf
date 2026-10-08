variable "location" {
  description = "Azure region for the CI infrastructure."
  type        = string
  default     = "mexicocentral"
}

variable "environment" {
  description = "Short environment name used in resource names and tags."
  type        = string
  default     = "ci"
}

variable "vnet_address_space" {
  description = "Address space of the CI virtual network."
  type        = string
  default     = "10.40.0.0/24"
}

variable "jenkins_subnet_prefix" {
  description = "Address prefix of the Jenkins subnet."
  type        = string
  default     = "10.40.0.0/27"
}

variable "jenkins_home_disk_size_gb" {
  description = "Size of the managed disk that holds JENKINS_HOME."
  type        = number
  default     = 32
}

variable "key_vault_admin_object_id" {
  description = "Entra ID object id allowed to write Key Vault secrets (az ad signed-in-user show --query id -o tsv)."
  type        = string
}
