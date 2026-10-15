# CAL-89 — Checks de Jenkins en Pull Requests

## Contexto y Objetivos

Conforme al ciclo 6 del roadmap técnico y la cadena crítica de CI (`CAL-48 → CAL-86 → CAL-87 → CAL-89 → CAL-70 → CAL-90`), el objetivo de **CAL-89** es conectar el servidor de CI privado en Azure con GitHub para que las corridas del pipeline (`Jenkinsfile`) emitan **checks de estado (commit statuses)** directamente en cada Pull Request.

Esto establece la compuerta de calidad automatizada que permitirá exigir el veredicto verde de Jenkins como requisito indispensable de fusión a `main`, preparando el camino para retirar GitHub Actions en CAL-90 una vez firmada la entrega CAL-70.

---

## Modelo de Red y Seguridad

Conforme a la decisión de arquitectura aceptada en `workflow/decisions/2026-10-07-acceso-privado-jenkins-azure.md`:

1. **Aislamiento perimetral estricto**:
   - El grupo de seguridad de red (`NSG`) en Azure aplica una regla `deny-internet-inbound` que niega todo tráfico entrante desde Internet.
   - El puerto web del controller (8080) escucha exclusivamente en `127.0.0.1` dentro de la máquina virtual `vm-jenkins`.
   - El acceso interactivo al panel y a la consola se realiza exclusivamente mediante **Tailscale** con cifrado HTTPS (`https://jenkins-restohub.<tailnet>.ts.net/`).
2. **Disparo por SCM Polling / Escaneo periódico**:
   - Debido al bloqueo perimetral de entrada, GitHub no puede enviar webhooks HTTP directamente al servidor.
   - Jenkins opera mediante un **job Multibranch Pipeline** que consulta la API de GitHub mediante sondeo periódico de ramas y PRs cada 2 minutos (`H/2 * * * *`).
3. **Egress seguro hacia la API de GitHub**:
   - La VM dispone de una IP pública estándar asignada a su NIC configurada exclusivamente para tráfico de salida (egress).
   - Jenkins realiza llamadas HTTPS seguras (`api.github.com`) autenticadas mediante un token con privilegios mínimos para emitir los estados de commit.

---

## Componentes y Configuración

### 1. Secreto en Azure Key Vault (`github-token`)

Jenkins obtiene las credenciales de GitHub dinámicamente desde Azure Key Vault mediante la identidad administrada (`id-jenkins`), sin almacenar tokens en texto plano ni en el repositorio:

```bash
# Crear o actualizar el token en Key Vault con tag de texto secreto
az keyvault secret set \
  --vault-name kv-restohub-ci \
  --name github-token \
  --value "<GITHUB_PERSONAL_ACCESS_TOKEN>" \
  --tags type=secret-text
```

El token de GitHub debe contar con el permiso `repo:status` (o acceso de lectura/escritura a estados de commit en tokens de grano fino). El plugin `azure-keyvault` importa automáticamente este secreto como una credencial de Jenkins con identificador `github-token`.

### 2. Configuración como Código (JCasC en `infra/jenkins/casc/jenkins.yaml`)

El servidor declara el endpoint y las credenciales de GitHub en su bloque `unclassified`:

```yaml
unclassified:
  location:
    url: "${JENKINS_LOCATION_URL}"
  githubpluginconfig:
    configs:
      - name: "GitHub"
        apiUrl: "https://api.github.com"
        credentialsId: "github-token"
        manageHooks: false
```

> **Nota:** `manageHooks: false` previene que Jenkins intente infructuosamente registrar webhooks públicos en GitHub.

### 3. Pipeline Declarativo (`Jenkinsfile`)

El pipeline incorpora:
- **`buildDiscarder(logRotator(numToKeepStr: '30'))`**: Preserva un historial máximo de 30 compilaciones para evitar el agotamiento de espacio en el disco persistente `/srv/jenkins`.
- **Acciones post-ejecución explícitas**: Registran mensajes de estado (`SUCCESS` / `FAILURE`) y garantizan la limpieza de contenedores temporales (`cleanWs`).

### 4. Configuración del Job Multibranch en Jenkins

En la consola administrativa de Jenkins (`https://jenkins-restohub.<tailnet>.ts.net/`):

1. **Nuevo elemento**: Seleccionar `Multibranch Pipeline` (ej. `restohub-pr-checks`).
2. **Branch Sources**:
   - Agregar fuente: `GitHub`.
   - Credenciales: `github-token`.
   - URL del repositorio: `https://github.com/LudwinGarcia1/callejon9-saas.git`.
   - Comportamientos:
     - *Discover branches*: Excluir ramas con PR abierto.
     - *Discover pull requests from origin*: Merging the pull request with current target branch revision.
     - *Discover pull requests from forks*: Confianza basada en colaboradores.
3. **Scan Repository Triggers**:
   - Marcar *Periodically if not otherwise run*.
   - Intervalo: `2 minutes`.
4. **Construcción y publicación automática de checks**:
   - Al detectar un PR nuevo o actualizado, Jenkins ejecuta el `Jenkinsfile`.
   - El plugin `github-branch-source` publica automáticamente el estado:
     - **Contexto**: `continuous-integration/jenkins/pr-merge`
     - **Estados**: `pending` al iniciar, `success` al aprobar todas las etapas, `failure` si alguna falla.
     - **Enlace**: Conduce al log de auditoría del build dentro de la red privada Tailscale.

---

## Protección de Ramas en GitHub

Una vez verificado el primer check emitido por Jenkins en un PR:

1. Ingresar a `https://github.com/LudwinGarcia1/callejon9-saas/settings/branches`.
2. Editar o crear la regla de protección para la rama `main`.
3. Activar la opción **«Require status checks to pass before merging»**.
4. Buscar y seleccionar el check:
   - **`continuous-integration/jenkins/pr-merge`**
5. Opcionalmente marcar **«Require branches to be up to date before merging»**.
6. Guardar los cambios.

A partir de este momento, GitHub bloquea el botón de merge si el pipeline de Jenkins en Azure no ha concluido con éxito.

---

## Verificación Operativa

Se proporcionan scripts automatizados para validar la configuración del repositorio y consultar la API de GitHub:

* **PowerShell (Windows)**:
  ```powershell
  .\scripts\verify-jenkins-pr-check.ps1
  ```
* **Bash (Linux/CI)**:
  ```bash
  bash scripts/verify-jenkins-pr-check.sh
  ```

Ambos scripts comprueban:
1. Sintaxis y directivas requeridas en `Jenkinsfile`.
2. Declaración de `githubpluginconfig` en `casc/jenkins.yaml`.
3. Consulta interactiva de estados de commit en GitHub mediante `gh api repos/LudwinGarcia1/callejon9-saas/commits/<SHA>/statuses`.

---

## Criterios de Aceptación Cubiertos

| Criterio | Estado | Evidencia |
|---|---|---|
| 1. Configuración de GitHub en JCasC | Verificado | `casc/jenkins.yaml` contiene `githubpluginconfig` con `credentialsId: "github-token"` y `manageHooks: false`. |
| 2. Gestión de historial en Jenkinsfile | Verificado | `buildDiscarder(logRotator(numToKeepStr: '30'))` en `options`. |
| 3. Compatibilidad con disparador privado | Verificado | Modelo SCM Polling documentado y respetado sin violar NSG. |
| 4. Notificaciones y estados de post-acción | Verificado | Bloques `success` y `failure` integrados en `Jenkinsfile`. |
| 5. Guía de integración en Key Vault y GitHub | Verificado | Procedimiento documentado con comandos reproducibles en `infra/jenkins/README.md` y este documento. |
