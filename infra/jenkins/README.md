# Jenkins privado en Azure (CAL-86)

Servidor de CI de RestoHub: un controller Jenkins LTS y un agente local en una VM de Azure, sin puertos públicos. La infraestructura base es la de CAL-48 (`infra/terraform/azure-ci/README.md`). La decisión de acceso está en `workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md`.

## Arquitectura

| Pieza | Dónde | Detalle |
|---|---|---|
| VM | `vm-jenkins` (`infra/terraform/azure-ci/jenkins.tf`) | Ubuntu 24.04 LTS, `Standard_D2as_v4` (2 vCPU, 8 GB, disco SCSI) por defecto, identidad `id-jenkins` |
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
| `github-token` | JCasC y `github-branch-source` para consultar PRs y reportar checks de estado a GitHub (CAL-89) | Token de GitHub (PAT) con alcance `repo:status` o `repo`; `az keyvault secret set --vault-name <vault> --name github-token --value <token> --tags type=string` |

El plugin se activa con las variables `AZURE_KEYVAULT_URL` y `AZURE_KEYVAULT_UAMI_ENABLED=true` ([documentación del plugin](https://plugins.jenkins.io/azure-keyvault/)). Los secretos con tag `type=username` (más el tag `username`) aparecen como credenciales de usuario/contraseña, y con `type=string` como Secret Text (el plugin rechaza `secret-text` con «Unknown type»).

## Primera puesta en marcha

Requiere la base de CAL-48 y, en la consola de Tailscale, MagicDNS y certificados HTTPS habilitados.

```powershell
cd infra/terraform/azure-ci
# 1. Base sin VM, para poder cargar los secretos antes del primer arranque.
terraform apply "-target=azurerm_role_assignment.key_vault_admin" "-target=azurerm_role_assignment.jenkins_secrets_reader"
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

### Leer el log de un build sin Tailscale (CAL-185)

Cuando un PR muestra `continuous-integration/jenkins/pr-merge` en rojo y no hay acceso a la tailnet, el log se lee desde la VM con la CLI de Azure. Basta una cuenta con acceso a `rg-restohub-ci`. Los comandos de esta sección solo leen; no cambian la VM ni Jenkins.

Ubicaciones dentro de la VM:

| Qué | Dónde |
|---|---|
| `JENKINS_HOME` | `/srv/jenkins/home`, montado en el contenedor `jenkins` |
| Carpeta de un PR | `/srv/jenkins/home/jobs/restohub/branches/PR-<n>*` |
| Log de un build | `<carpeta del PR>/builds/<número>/log` |

El número del build es el que aparece en la URL del check de GitHub; por ejemplo, `.../job/PR-128/2/...` es el build `2` de `PR-128`.

Todos los comandos siguen esta forma:

```text
az vm run-command invoke -g rg-restohub-ci -n vm-jenkins --command-id RunShellScript --query "value[0].message" -o tsv --scripts '<script>'
```

Pasa el script **entre comillas simples**. Con comillas dobles, la terminal local expande `$(...)` y `$d` antes de enviarlo, y la VM recibe rutas vacías como `/builds/2/log`. Cada invocación tarda entre medio minuto y un minuto, y la VM ejecuta una sola a la vez.

**1. Etapas y errores del build.** Sustituye `PR-128` y el número de build. El log incluye líneas `ha://`, anotaciones internas de Jenkins sin texto útil, que se filtran:

```bash
d=$(find /srv/jenkins/home/jobs -maxdepth 4 -type d -name "PR-128*" | head -1); f="$d/builds/2/log"; grep -av "^ha:" "$f" | sed "s/\x1b\[[0-9;]*m//g" | grep -anE "\[CI\]|ERROR|rror:|ELIFECYCLE|exit code|BUILD|No space|Killed" | tail -n 40 | cut -c1-220
```

Los mensajes `[CI]` del `Jenkinsfile` marcan dónde empieza cada paso. El último que aparece antes de `[CI] Limpiando contenedores...` es el paso que falló. Las líneas `ERROR` del backend con `BUILD SUCCESS` después son registros de pruebas que comprueban errores a propósito.

**2. Final del log**, con el código de salida:

```bash
d=$(find /srv/jenkins/home/jobs -maxdepth 4 -type d -name "PR-128*" | head -1); grep -av "^ha:" "$d/builds/2/log" | sed "s/\x1b\[[0-9;]*m//g" | tail -n 60 | cut -c1-220
```

**3. Recursos y servicios de la VM**, para descartar la infraestructura:

```bash
df -h / /srv/jenkins | tail -2; free -m | head -2; docker ps --format "{{.Names}} {{.Image}} {{.Status}}"; systemctl is-active jenkins-agent
```

El controller corre en el contenedor `jenkins`, así que es normal que `systemctl is-active jenkins` diga `inactive`.

**Qué hacer según el resultado:**

- **Un paso termina con `exit code` distinto de cero:** el pipeline falló de verdad. Repite ese paso en local con los mismos comandos del `Jenkinsfile`. Si GitHub Actions pasa en el mismo commit, busca diferencias de entorno entre los dos, como versiones de Node, pnpm o JDK.
- **Disco casi lleno, poca memoria o el contenedor `jenkins` detenido:** es un problema de la VM y no del código. Consulta [Recuperación](#recuperación) antes de reiniciar o reemplazar nada.
- **El log no existe o está vacío:** el build no llegó a empezar. Revisa el estado del agente en el panel, o con `systemctl status jenkins-agent`.

GitHub muestra un build con `Finished: FAILURE` como «This commit cannot be built». El texto no distingue entre un fallo del pipeline y uno de la infraestructura; solo el log lo aclara.

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
| VM perdida o corrupta | `terraform apply "-replace=azurerm_linux_virtual_machine.jenkins"` | Todo: configuración, historial, credenciales y nodo de Tailscale |
| Disco de `JENKINS_HOME` perdido | `terraform apply` crea un disco vacío; se carga una clave nueva de Tailscale | Configuración y credenciales (vienen del código y de Key Vault); **se pierde el historial de builds** |

## Evidencia de los criterios de CAL-86

| Criterio | Cómo se demuestra |
|---|---|
| 1. Solo por acceso privado | Desde fuera de la tailnet, la IP pública no responde en ningún puerto; `az network nsg rule list` muestra `deny-internet-inbound`; dentro de la tailnet, el panel pide login |
| 2. Recreación conserva estado | `terraform apply "-replace=azurerm_linux_virtual_machine.jenkins"`: el mismo usuario entra, el nodo `local-agent` vuelve en línea y el historial sigue ahí |
| 3. Agente sin instalación manual | Por Tailscale SSH, como `jenkins-agent`: `java -version`, `node --version`, `pnpm --version` dentro de `frontend/`, `docker run --rm hello-world`. La corrida de Testcontainers llega con CAL-88 y CAL-87 |
| 4. Identidad administrada con alcance mínimo | Desde la VM, con un token de IMDS: leer un secreto del vault responde 200; `GET https://management.azure.com/subscriptions/<id>/resourcegroups?api-version=2021-04-01` responde 403 `AuthorizationFailed` |
| 5. Restauración ensayada con duración | Cronometrar el criterio 2 desde el `apply` hasta que el panel responde y el agente está en línea; registrar el tiempo en el issue |

**Costo:** se registra con datos reales de Cost Management después de una semana de operación. No se estima aquí.

## Verificación en Azure (2026-10-13)

- Infraestructura base de CAL-48 provisionada con Terraform en `eastus` (VNet privada, NSG con `deny-internet-inbound`, IP de salida, Managed Disk de 32 GB, Key Vault con RBAC).
- `vm-jenkins` (Ubuntu 24.04 LTS, `Standard_D2as_v4`) en ejecución con `custom_data` cloud-init.
- Controller en contenedor Docker publicado exclusivamente en `127.0.0.1:8080` (HTTP 200 en `/login`).
- `jenkins-agent.service` conectado vía WebSocket al controller. Temurin JDK 21, Node 24, pnpm y Docker verificados bajo el usuario `jenkins-agent`.
- Acceso seguro por Tailscale con HTTPS (`https://jenkins-restohub.<tailnet>.ts.net/`) probado e inicio de sesión exitoso.
- Mínimo privilegio confirmado: la identidad administrada lee secretos de Key Vault con HTTP 200 y es denegada en ARM con HTTP 403 `AuthorizationFailed`.

## Pipeline de CI (CAL-87)

El archivo `Jenkinsfile` en la raíz del repositorio orquesta las puertas de calidad obligatorias:

1. **`Prepare PostgreSQL`**: Levanta un contenedor Docker efímero `postgres:16`, espera a que esté listo e inicializa los roles (`callejon9_owner`, `callejon9_app`) y bases de datos con `scripts/setup-db.sql`.
2. **`Security Scan`**: Ejecuta `scripts/security-scan.sh` para auditoría SCA (`pnpm audit`), SAST (`SpotBugs`) y detección de secretos (CAL-14).
3. **`Backend Quality Gates`**: Ejecuta `./mvnw -B verify` bajo Java 21, pasando las pruebas de RLS, contratos y concurrencia. Publica resultados con `junit`.
4. **`Frontend Quality Gates`**: Ejecuta `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm exec tsc --noEmit`, `pnpm test` (pruebas unitarias Vitest) y `pnpm build`.
5. **`Mobile Quality Gates`**: En `mobile/`, ejecuta `pnpm install --frozen-lockfile`, `pnpm exec tsc --noEmit` y `pnpm test`.
6. **`Production Images`**: Construye y verifica imágenes mínimas y no-root con `scripts/verify-images.sh` (CAL-17).
7. **`Post Actions`**: Detiene y limpia el contenedor de PostgreSQL y purga el espacio de trabajo con `cleanWs()`.

## Checks de Jenkins en Pull Requests (CAL-89)

Para que Jenkins en Azure gobierne la integración y bloquee o apruebe PRs antes del merge a `main`:

### 1. Modelo de disparo por SCM Polling
Conforme a `workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md`, el NSG niega tráfico entrante desde Internet. GitHub no puede invocar webhooks entrantes directamente en Jenkins.
* **Mecanismo**: El job Multibranch Pipeline de Jenkins consulta periódicamente la API de GitHub mediante **SCM Polling / Periodic Folder Indexing** (cada 2 minutos: `H/2 * * * *`).
* **Descubrimiento de PRs**: El plugin `github-branch-source` indexa automáticamente:
  * Pull requests originados en ramas del repositorio (`origin`).
  * Pull requests de forks confiables con permisos de colaboradores.
* **Egress HTTPS**: La salida a `api.github.com` viaja por la IP pública de egress asignada a la NIC de `vm-jenkins`.

### 2. Publicación de estados en GitHub
El plugin `github-branch-source` y la configuración JCasC (`githubpluginconfig`) autentican con el token de Key Vault (`github-token`) y reportan el estado del commit a la API de GitHub:
* **Contexto del check**: `continuous-integration/jenkins/pr-merge` (o `continuous-integration/jenkins/branch`).
* **Estados**:
  * `PENDING`: Al iniciar la compilación.
  * `SUCCESS`: Tras pasar satisfactoriamente todas las etapas del pipeline.
  * `FAILURE`: Ante cualquier falla en backend, frontend, app móvil, seguridad o imágenes. GitHub lo muestra como «This commit cannot be built». Para saber qué falló, consulta [Leer el log de un build sin Tailscale](#leer-el-log-de-un-build-sin-tailscale-cal-185).
* **URL de destino**: Apunta a la URL segura en Tailscale (`https://jenkins-restohub.../job/...`) para auditoría de logs.

### 3. Protección de ramas en GitHub
Una vez validado el primer check reportado en un PR:
1. En GitHub: `Settings` > `Branches` > Regla de protección para `main`.
2. Habilitar **«Require status checks to pass before merging»**.
3. Buscar y marcar **`continuous-integration/jenkins/pr-merge`**.
4. Con esto, ningún PR puede integrarse a `main` sin el veredicto en verde de Jenkins en Azure, habilitando el retiro posterior de GitHub Actions en CAL-90.
