output "resource_group_name" {
  value = azurerm_resource_group.ci.name
}

output "location" {
  value = azurerm_resource_group.ci.location
}

output "jenkins_subnet_id" {
  value = azurerm_subnet.jenkins.id
}

output "jenkins_egress_public_ip_id" {
  value = azurerm_public_ip.jenkins_egress.id
}

output "jenkins_egress_public_ip_address" {
  value = azurerm_public_ip.jenkins_egress.ip_address
}

output "jenkins_identity_id" {
  value = azurerm_user_assigned_identity.jenkins.id
}

output "jenkins_identity_client_id" {
  value = azurerm_user_assigned_identity.jenkins.client_id
}

output "key_vault_id" {
  value = azurerm_key_vault.ci.id
}

output "key_vault_uri" {
  value = azurerm_key_vault.ci.vault_uri
}

output "jenkins_home_disk_id" {
  value = azurerm_managed_disk.jenkins_home.id
}

output "jenkins_vm_id" {
  value = azurerm_linux_virtual_machine.jenkins.id
}

output "jenkins_url" {
  value = var.jenkins_url
}
