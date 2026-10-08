# Infraestructura de CI en Azure (CAL-48)

Base de Azure sobre la que CAL-86 instala Jenkins. Se aprovisiona solo con Terraform; ningún recurso de este módulo se crea desde el portal.

La decisión de acceso privado y salida a internet está en `workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md`.

## Estructura

| Root | Estado | Qué crea |
|---|---|---|
| `bootstrap/` | Local (`bootstrap/terraform.tfstate`, ignorado por git) | Grupo `rg-restohub-tfstate`, storage account del estado remoto y contenedor `tfstate` |
| `.` (este directorio) | Remoto, en el contenedor `tfstate` | Base de CI descrita abajo |

El estado remoto usa el backend `azurerm`, que bloquea el estado con un *lease* del blob, y autenticación de Entra ID (`use_azuread_auth`); el storage account tiene deshabilitadas las claves compartidas.

## Recursos de la base de CI

| Recurso | Nombre | Para qué |
|---|---|---|
| Resource group | `rg-restohub-ci` | Contenedor de todo lo de CI |
| VNet y subred | `vnet-restohub-ci`, `snet-jenkins` | Red privada; la subred no tiene salida implícita (`default_outbound_access_enabled = false`) |
| NSG | `nsg-jenkins` | Regla `deny-internet-inbound`: ningún puerto entrante desde Internet |
| IP pública estándar | `pip-jenkins-egress` | Solo salida (GitHub, Maven Central, npm, Docker Hub); CAL-86 la asocia a la NIC |
| Identidad administrada | `id-jenkins` | Identidad de la VM; solo tiene `Key Vault Secrets User` sobre el vault |
| Key Vault | `kv-rh-ci-<sufijo>` | Secretos de Jenkins y la clave de registro de Tailscale; autorización por RBAC |
| Disco administrado | `disk-jenkins-home` | `JENKINS_HOME`, separado del ciclo de vida de la VM |

El sufijo de los nombres globales se deriva de un hash del id de la suscripción: es estable y no requiere variables.

### Salidas que consume CAL-86

`resource_group_name`, `location`, `jenkins_subnet_id`, `jenkins_egress_public_ip_id`, `jenkins_identity_id`, `jenkins_identity_client_id`, `key_vault_id`, `key_vault_uri` y `jenkins_home_disk_id`. El endpoint privado de Jenkins es la dirección de la VM en la tailnet, que existe hasta que CAL-86 registra la VM en Tailscale.

## Requisitos

- Terraform 1.9 o superior y Azure CLI.
- Una cuenta con permiso para crear asignaciones de rol en la suscripción (Owner, o Contributor más User Access Administrator).
- Suscripción objetivo: Azure for Students, región East US (`eastus`). La suscripción tiene asignada la política «Allowed resource deployment regions», que solo permite `westus`, `eastus`, `centralus`, `westus2` y `canadacentral` (verificado con `az policy assignment list` el 2026-10-08). Cualquier otra región se rechaza en el `apply`.

## Procedimiento

Todos los comandos son de PowerShell y se corren desde `infra/terraform/azure-ci`.

### 1. Sesión

```powershell
az login
$env:ARM_SUBSCRIPTION_ID = az account show --query id -o tsv
$operator = az ad signed-in-user show --query id -o tsv
```

### 2. Estado remoto (una sola vez)

```powershell
terraform -chdir=bootstrap init
terraform -chdir=bootstrap apply -var "state_admin_object_id=$operator"
terraform -chdir=bootstrap output backend_config
```

Guarda `bootstrap/terraform.tfstate` fuera del repositorio. Si se pierde, los recursos siguen existiendo y se recuperan con `terraform import`.

La asignación de rol sobre el storage account puede tardar unos minutos en propagarse; si el paso 3 responde 403, espera y repite.

### 3. Base de CI

Copia `backend.hcl.example` a `backend.hcl` con el nombre del storage account que imprimió el bootstrap, y `terraform.tfvars.example` a `terraform.tfvars` con el valor de `$operator`. Ambos archivos están ignorados por git.

```powershell
terraform init -backend-config=backend.hcl
terraform plan -out tfplan
terraform show -no-color tfplan > "$env:TEMP\plan-cal-48.txt"
terraform apply tfplan
```

El plan incluye identificadores de la suscripción: se guarda fuera del repositorio y se adjunta al issue.

## Puerta de calidad

```powershell
.\scripts\terraform-check.ps1
```

Corre `terraform fmt -check` y `terraform validate` en ambos roots sin backend ni credenciales. Falla con código distinto de cero ante cualquier diferencia de formato o error de validación.

## Evidencia de los criterios de CAL-48

| Criterio | Cómo se demuestra |
|---|---|
| 1. Crea solo lo declarado | Comparar el `plan` con `az resource list -g rg-restohub-ci --query "[].{name:name,type:type}" -o table` |
| 2. `fmt`, `validate` y `plan` revisables | Salida de `.\scripts\terraform-check.ps1` y `plan-cal-48.txt` adjuntos al issue |
| 3. El bloqueo impide corrupción | Lanzar dos `terraform plan` a la vez: el segundo debe fallar al adquirir el lock del estado |
| 4. Sin claves de larga duración | `az role assignment list --assignee <jenkins_identity_client_id> --all -o table` muestra solo `Key Vault Secrets User` sobre el vault; el repositorio no contiene secretos |
| 5. Red, persistencia y acceso mínimo verificados | `terraform output` y `az network nsg rule list -g rg-restohub-ci --nsg-name nsg-jenkins -o table` |
| 6. Destrucción sin huérfanos | `terraform destroy`; luego `az group exists -n rg-restohub-ci` devuelve `false` y `az keyvault list-deleted` no lista el vault |

El hito de **inicio de infraestructura** se registra con el primer `apply`, y el de **infraestructura terminada** con la verificación de la tabla y la destrucción en un entorno de prueba.

El provider purga el Key Vault al destruirlo (`purge_soft_delete_on_destroy`) y el resource group se niega a borrarse si contiene recursos que Terraform no administra, lo que deja a la vista cualquier huérfano.

## Riesgos aceptados

- **Key Vault con acceso de red público.** El acceso se controla solo por RBAC. Restringirlo por red exigiría un endpoint privado y DNS privado, que agregan costo; se revisa si el vault llega a guardar algo más que credenciales de CI.
- **Estado del bootstrap local.** Contiene identificadores, no secretos. Perderlo no destruye nada.
