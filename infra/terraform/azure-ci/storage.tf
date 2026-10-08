# JENKINS_HOME lives on its own disk so the VM can be recreated without losing state.
resource "azurerm_managed_disk" "jenkins_home" {
  name                 = "disk-jenkins-home"
  resource_group_name  = azurerm_resource_group.ci.name
  location             = azurerm_resource_group.ci.location
  storage_account_type = "StandardSSD_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.jenkins_home_disk_size_gb
  tags                 = local.tags
}
