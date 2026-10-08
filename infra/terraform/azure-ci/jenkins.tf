locals {
  jenkins_source_dir = "${path.module}/../../jenkins"
  jenkins_files      = [for f in fileset(local.jenkins_source_dir, "**") : f if f != "README.md"]

  # Every file under infra/jenkins travels in cloud-init; changing any of them replaces the VM,
  # while JENKINS_HOME survives on its own disk.
  jenkins_cloud_init = join("\n", ["#cloud-config", yamlencode({
    write_files = concat(
      [{
        path        = "/etc/restohub/ci.env"
        permissions = "0644"
        content = join("\n", [
          "KEY_VAULT_URI=${azurerm_key_vault.ci.vault_uri}",
          "TAILSCALE_HOSTNAME=${var.tailscale_hostname}",
          "JENKINS_LOCATION_URL=${var.jenkins_url}",
          "",
        ])
      }],
      [for f in local.jenkins_files : {
        path        = "/opt/restohub/jenkins/${f}"
        encoding    = "b64"
        content     = filebase64("${local.jenkins_source_dir}/${f}")
        permissions = endswith(f, ".sh") ? "0755" : "0644"
      }],
    )
    runcmd = [["bash", "/opt/restohub/jenkins/provision.sh"]]
  })])
}

resource "azurerm_network_interface" "jenkins" {
  name                = "nic-jenkins"
  resource_group_name = azurerm_resource_group.ci.name
  location            = azurerm_resource_group.ci.location
  tags                = local.tags

  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.jenkins.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.jenkins_egress.id
  }
}

resource "azurerm_linux_virtual_machine" "jenkins" {
  name                  = "vm-jenkins"
  resource_group_name   = azurerm_resource_group.ci.name
  location              = azurerm_resource_group.ci.location
  size                  = var.jenkins_vm_size
  admin_username        = "azureuser"
  network_interface_ids = [azurerm_network_interface.jenkins.id]
  custom_data           = base64encode(local.jenkins_cloud_init)
  tags                  = local.tags

  admin_ssh_key {
    username   = "azureuser"
    public_key = var.admin_ssh_public_key
  }

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.jenkins.id]
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "StandardSSD_LRS"
    disk_size_gb         = 64
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # Managed boot diagnostics enable the serial console, the emergency path if Tailscale is down.
  boot_diagnostics {}

  # The VM reads Key Vault on its first boot.
  depends_on = [azurerm_role_assignment.jenkins_secrets_reader]
}

resource "azurerm_virtual_machine_data_disk_attachment" "jenkins_home" {
  managed_disk_id    = azurerm_managed_disk.jenkins_home.id
  virtual_machine_id = azurerm_linux_virtual_machine.jenkins.id
  lun                = 0
  caching            = "ReadWrite"
}
