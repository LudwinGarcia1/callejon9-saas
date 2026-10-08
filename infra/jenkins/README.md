# Jenkins privado en Azure (CAL-86)

Servidor de CI de RestoHub: un controller Jenkins LTS y un agente local en una VM de Azure, sin puertos públicos. La infraestructura base es la de CAL-48 (`infra/terraform/azure-ci/README.md`). La decisión de acceso está en `workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md`.

## Arquitectura

| Pieza | Dónde | Detalle |
|---|---|---|
| VM | `vm-jenkins` (`infra/terraform/azure-ci/jenkins.tf`) | Ubuntu 24.04 LTS, `Standard_B2ms` por defecto, identidad `id-jenkins` |
| Controller | Contenedor `jenkins` (`systemd/jenkins-controller.service`) | Imagen `Dockerfile`: Jenkins `2.580.1-lts-jdk21` fijada por digest; publicado solo en `127.0.0.1:8080`; 0 ejecutores |
| Plugins | `plugins.txt` | Lock completo de 83 plugins, incluidas dependencias transitivas |
| Configuración | `casc/jenkins.yaml` | JCasC: usuario `admin`, sin registro, sin lectura anónima, nodo `local-agent` |
| Agente | Servicio `jenkins-agent` (`systemd/jenkins-agent.service`) | Usuario `jenkins-agent` en el host, conectado por WebSocket; JDK 21, Node 24, pnpm (corepack), Docker |
| `JENKINS_HOME` | Disco `disk-jenkins-home` montado en `/srv/jenkins` | También guarda el estado de Tailscale y el secreto del agente |
| Acceso | Tailscale | `tailscale serve` publica Jenkins por HTTPS solo dentro de la tailnet; Tailscale SSH para administrar |

`provision.sh` corre en cada VM nueva vía cloud-init. Monta el disco (solo lo formatea si está vacío), instala los paquetes, construye la imagen, arranca los servicios y une la VM a la tailnet.

Cualquier cambio en un archivo de este directorio (salvo este README) cambia el `custom_data` y Terraform **reemplaza la VM**. `JENKINS_HOME` sobrevive porque vive en su propio disco.

### Riesgo aceptado: controller y agente en la misma VM

El agente pertenece al grupo `docker`, lo que en la práctica equivale a root en el host, así que un build podría leer `JENKINS_HOME`. Se aceptó para mantener una sola VM. Se revisa si el CI llega a construir código de terceros (pull requests de forks).

## Secretos en Key Vault

| Secreto | Lo usa | Cómo se crea |
|---|---|---|
| `jenkins-admin-password` | JCasC, vía el plugin Azure Key Vault con la identidad administrada | `az keyvault secret set --vault-name <vault> --name jenkins-admin-password --value <contraseña>` |
| `tailscale-auth-key` | `provision.sh`, solo cuando el disco aún no tiene identidad de Tailscale | Clave de Tailscale *pre-approved*, de preferencia con tag; `az keyvault secret set ... --name tailscale-auth-key` |

El plugin se activa con las variables `AZURE_KEYVAULT_URL` y `AZURE_KEYVAULT_UAMI_ENABLED=true` ([documentación del plugin](https://plugins.jenkins.io/azure-keyvault/)). Los secretos con tag `type=username` (más el tag `username`) aparecen como credenciales de Jenkins; ahí va el token del repositorio que use CAL-87.

## Primera puesta en marcha

Requiere la base de CAL-48 y, en la consola de Tailscale, MagicDNS y certificados HTTPS habilitados.

```powershell
cd infra/terraform/azure-ci
# 1. Base sin VM, para poder cargar los secretos antes del primer arranque.
terraform apply -target="azurerm_role_assignment.key_vault_admin" -target="azurerm_role_assignment.jenkins_secrets_reader"
# 2. Secretos (tabla anterior).
# 3. Todo lo demás, incluida la VM.
terraform apply
```

Seguimiento del aprovisionamiento: `az vm run-command invoke -g rg-restohub-ci -n vm-jenkins --command-id RunShellScript --scripts "tail -n 50 /var/log/cloud-init-output.log"`.

Si `provision.sh` se detuvo (por ejemplo, porque faltaba un secreto), es seguro volver a correrlo: `--scripts "bash /opt/restohub/jenkins/provision.sh"`.

## Acceso

- Panel: `https://<tailscale_hostname>.<tailnet>.ts.net/`, solo desde un dispositivo de la tailnet.
- Shell: `tailscale ssh azureuser@<tailscale_hostname>`.
- Emergencia, sin Tailscale: `az vm run-command invoke` o la consola serie del portal (boot diagnostics habilitado).

## Actualización

1. **Jenkins:** cambia juntos el tag y el digest en `Dockerfile`.
2. **Plugins:** edita las versiones directas y regenera el lock con la misma imagen:

   ```bash
   docker run --rm --entrypoint bash -v "$PWD/plugins.txt:/tmp/plugins.txt:ro" <imagen-del-Dockerfile> \
     -c 'jenkins-plugin-cli --plugin-file /tmp/plugins.txt --no-download --list'
   ```

   Copia la sección «Resulting plugin list» como `nombre:versión`.
3. **Prueba local** antes del PR: `docker build` y un `docker run` con un archivo `/run/secrets/jenkins-admin-password`; debe llegar a «Jenkins is fully up and running» sin líneas `SEVERE`.
4. `terraform apply` reemplaza la VM con la nueva configuración.

Los parches del sistema operativo los aplica `unattended-upgrades` de Ubuntu; recrear la VM la deja en la imagen más reciente.

## Recuperación

| Incidente | Procedimiento | Qué se conserva |
|---|---|---|
| VM perdida o corrupta | `terraform apply -replace="azurerm_linux_virtual_machine.jenkins"` | Todo: configuración, historial, credenciales y nodo de Tailscale |
| Disco de `JENKINS_HOME` perdido | `terraform apply` crea un disco vacío; se carga una clave nueva de Tailscale | Configuración y credenciales (vienen del código y de Key Vault); **se pierde el historial de builds** |

## Evidencia de los criterios de CAL-86

| Criterio | Cómo se demuestra |
|---|---|
| 1. Solo por acceso privado | Desde fuera de la tailnet, la IP pública no responde en ningún puerto; `az network nsg rule list` muestra `deny-internet-inbound`; dentro de la tailnet, el panel pide login |
| 2. Recreación conserva estado | `terraform apply -replace=...vm-jenkins`: el mismo usuario entra, el nodo `local-agent` vuelve en línea y el historial sigue ahí |
| 3. Agente sin instalación manual | Por Tailscale SSH, como `jenkins-agent`: `java -version`, `node --version`, `pnpm --version` dentro de `frontend/`, `docker run --rm hello-world`. La corrida de Testcontainers llega con CAL-88 y CAL-87 |
| 4. Identidad administrada con alcance mínimo | Desde la VM, con un token de IMDS: leer un secreto del vault responde 200; `GET https://management.azure.com/subscriptions/<id>/resourcegroups?api-version=2021-04-01` responde 403 `AuthorizationFailed` |
| 5. Restauración ensayada con duración | Cronometrar el criterio 2 desde el `apply` hasta que el panel responde y el agente está en línea; registrar el tiempo en el issue |

**Costo:** se registra con datos reales de Cost Management después de una semana de operación. No se estima aquí.

## Verificación hecha sin Azure (2026-10-08)

- La imagen construye; con un `JENKINS_HOME` vacío arranca sin `SEVERE`. Anónimo recibe 403, una contraseña incorrecta 401 y `admin` 200. El nodo integrado tiene 0 ejecutores.
- Un agente conectado por WebSocket con el secreto publicado queda en línea.
- Al destruir y recrear el contenedor sobre el mismo `JENKINS_HOME`, el controller vuelve en 38 s con el mismo secreto de agente, y el agente se reconecta (54 s en total).
- `install_packages` en `ubuntu:24.04` instala Temurin 21.0.12, Node v24.21.0, pnpm 10.28.2 (la versión de `frontend/package.json`), Docker 29.8.2 con buildx, Tailscale 1.104.1, git y jq.
- `shellcheck` sin hallazgos en `provision.sh`.

Falta probar en Azure: el disco, la identidad, Key Vault, Tailscale, systemd y el `plan`/`apply`.
