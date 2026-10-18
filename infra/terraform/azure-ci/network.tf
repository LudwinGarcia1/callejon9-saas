resource "azurerm_virtual_network" "ci" {
  name                = "vnet-${local.name}"
  resource_group_name = azurerm_resource_group.ci.name
  location            = azurerm_resource_group.ci.location
  address_space       = [var.vnet_address_space]
  tags                = local.tags
}

# Private subnet: no implicit outbound access; egress goes through the explicit public IP.
resource "azurerm_subnet" "jenkins" {
  name                            = "snet-jenkins"
  resource_group_name             = azurerm_resource_group.ci.name
  virtual_network_name            = azurerm_virtual_network.ci.name
  address_prefixes                = [var.jenkins_subnet_prefix]
  default_outbound_access_enabled = false
}

resource "azurerm_network_security_group" "jenkins" {
  name                = "nsg-jenkins"
  resource_group_name = azurerm_resource_group.ci.name
  location            = azurerm_resource_group.ci.location
  tags                = local.tags
}

# Administrative access goes through Tailscale, which only needs outbound traffic.
resource "azurerm_network_security_rule" "deny_internet_inbound" {
  name                        = "deny-internet-inbound"
  description                 = "No public inbound access; see workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md"
  resource_group_name         = azurerm_resource_group.ci.name
  network_security_group_name = azurerm_network_security_group.jenkins.name
  priority                    = 4000
  direction                   = "Inbound"
  access                      = "Deny"
  protocol                    = "*"
  source_address_prefix       = "Internet"
  source_port_range           = "*"
  destination_address_prefix  = "*"
  destination_port_range      = "*"
}

resource "azurerm_subnet_network_security_group_association" "jenkins" {
  subnet_id                 = azurerm_subnet.jenkins.id
  network_security_group_id = azurerm_network_security_group.jenkins.id
}

# Explicit egress for GitHub, Maven Central, npm and Docker Hub. CAL-86 attaches it to the VM NIC.
resource "azurerm_public_ip" "jenkins_egress" {
  name                = "pip-jenkins-egress"
  resource_group_name = azurerm_resource_group.ci.name
  location            = azurerm_resource_group.ci.location
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = local.tags
}
