variable "location" {
  description = "Azure region for the CI infrastructure."
  type        = string
  default     = "eastus"
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

variable "jenkins_vm_size" {
  description = "VM size for the Jenkins controller and its local agent."
  type        = string
  default     = "Standard_B2ms"
}

variable "admin_ssh_public_key" {
  description = "SSH public key for the VM admin user. Day-to-day access uses Tailscale SSH."
  type        = string
}

variable "tailscale_hostname" {
  description = "Machine name of the Jenkins VM in the tailnet."
  type        = string
  default     = "jenkins-restohub"
}

variable "jenkins_url" {
  description = "Jenkins URL inside the tailnet, e.g. https://jenkins-restohub.<tailnet>.ts.net/"
  type        = string
}

variable "key_vault_admin_object_id" {
  description = "Entra ID object id allowed to write Key Vault secrets (az ad signed-in-user show --query id -o tsv)."
  type        = string
}
