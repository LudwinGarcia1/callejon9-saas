# Backend de Callejón 9 en Azure

Infraestructura para publicar la API de Spring Boot en Azure. El frontend se aloja en Azure App Service, en otra suscripción (`infra/terraform/azure-frontend`), y consume esta API a través de su propio servidor Next.js. Se aprovisiona solo con Terraform, en la suscripción Azure for Students del equipo, y es independiente de `infra/terraform/azure-ci` (otra suscripción y otro estado).

## Arquitectura

```
Navegador ──HTTPS──► Frontend Next.js (App Service, otra suscripción)
                       │  rewrites /api/v1/* del lado del servidor
                       ▼ HTTPS
              App Service B1 Linux · Java 21 SE · 1 instancia
                 │ identidad administrada ──► Key Vault (JWT y contraseñas)
                 │ TLS (sslmode=require)
                 ▼
              PostgreSQL Flexible Server B1ms · v16 · 32 GB
              firewall: solo las IP de salida de la Web App
```

| Recurso | Nombre | Para qué |
|---|---|---|
| Resource group | `rg-callejon9-<env>-mxc-001` | Contenedor de todo el backend |
| App Service plan | `asp-callejon9-<env>-mxc-001` | B1 Linux: el nivel más barato con Always On. Una sola instancia, porque los límites de login y alta y el registro de WebSockets viven en memoria |
| Web App | `app-callejon9-api-<env>-<hash>` | JAR de Spring Boot sobre Java 21 SE. HTTPS obligatorio, TLS 1.2, FTP y publicación con contraseña desactivados, health check en `/actuator/health/liveness`, que no depende de la base: una caída de PostgreSQL no hace que App Service reemplace la instancia (ver `docs/operacion-probes.md`) |
| PostgreSQL Flexible Server | `psql-callejon9-<env>-<hash>` | B1ms, versión 16 (la que usan las pruebas), sin alta disponibilidad ni respaldo geo-redundante. Permite `pgcrypto`, que exige la migración `V1` |
| Base de datos | `callejon9` | La crea Terraform; los roles los crea `scripts/setup-db-azure.sql` |
| Key Vault | `kv-c9-<env>-<hash>` | `jwt-secret`, `db-app-password`, `db-owner-password` y `postgres-admin-password`, generados con `random_password` |
| Log Analytics y Application Insights | `log-…`, `appi-…` | Opcionales (`monitoring_enabled`), con tope de 0.1 GB diarios |
| Presupuesto | `budget-…` | Opcional (`budget_contact_emails`): alertas al 80 % real y 100 % pronosticado, sobre toda la suscripción |

**Lo que no se crea, a propósito:** VNet, endpoints privados y DNS privado (unos 7.30 USD al mes por endpoint, y bloquearían el bootstrap de roles desde la laptop del operador), Container Registry (se despliega el JAR), Storage (el backend no guarda archivos: los PDF se generan en memoria), Redis (una instancia basta) y Front Door, Application Gateway o API Management (App Service ya ofrece TLS y dominio).

## Costo estimado

Precios de lista en `mexicocentral` según la API pública de precios de Azure (consultada el 2026-10-15), con 730 horas al mes.

| Recurso | USD al mes |
|---|---|
| App Service B1 Linux | 13.65 |
| PostgreSQL B1ms con 32 GB | 0 durante los 12 meses de servicios gratuitos (750 h de B1ms y 32 GB). Sin el beneficio: 17.70 |
| Key Vault | < 0.10 (por operaciones) |
| Log Analytics y Application Insights | 0 dentro de la cuota gratuita mensual, gracias al tope diario |
| Estado de Terraform (bootstrap) | < 0.05 |
| **Total** | **≈ 13.70** con el beneficio; **≈ 31.40** sin él |

Las 750 horas gratuitas alcanzan para **un** servidor B1ms encendido todo el mes. Un segundo servidor encendido en la misma suscripción consume el mismo beneficio.

El plan B1 se cobra mientras exista, aunque la Web App esté detenida. Para dejar de pagar hay que destruirlo (`terraform destroy`) o bajarlo a `F1`. Un servidor PostgreSQL detenido deja de cobrar cómputo, pero Azure lo vuelve a encender solo a los 7 días. `scripts/azure-backend-power.ps1` hace ambas cosas sin borrar nada; ver [Apagar y encender el backend](#apagar-y-encender-el-backend).

## Requisitos

- Terraform 1.9 o superior, Azure CLI y `psql`.
- Una cuenta con permiso para crear asignaciones de rol en la suscripción (Owner).
- JDK 21 para compilar el JAR.

Para sumar a otra persona a un entorno que ya existe, ve a [Sumar a otro operador](#sumar-a-otro-operador).

## Procedimiento

Todos los comandos son de PowerShell y se corren desde `infra/terraform/azure-backend`, salvo que se indique otra ruta.

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

Guarda `bootstrap/terraform.tfstate` fuera del repositorio. La asignación de rol sobre el storage account puede tardar unos minutos; si el paso siguiente responde 403, espera y repite.

### 3. Variables

Copia `backend.hcl.example` a `backend.hcl` con el storage account que imprimió el bootstrap, y `terraform.tfvars.example` a `terraform.tfvars` con el origen público del frontend, sus IP de salida (`trusted_proxy_addresses`) y el valor de `$operator`. Ambos archivos están ignorados por git.

### 4. Primer apply, en dos pasos

Las reglas de firewall de PostgreSQL se generan a partir de las IP de salida de la Web App, que solo se conocen cuando la Web App existe:

```powershell
terraform init "-backend-config=backend.hcl"
terraform apply "-target=azurerm_linux_web_app.api"
terraform apply
```

El primer paso crea también lo que la Web App necesita: el servidor, la base, el vault y sus secretos. Si un secreto falla con 403, la asignación de rol del vault todavía se está propagando: espera un minuto y repite.

### 5. Roles de la base (una sola vez)

Abre el firewall a tu IP pública, ejecuta el bootstrap y vuelve a cerrarlo:

```powershell
$myIp = (Invoke-RestMethod https://api.ipify.org)
terraform apply -var "operator_ip_address=$myIp"

$kv    = terraform output -raw key_vault_name
$fqdn  = terraform output -raw postgres_fqdn
$admin = terraform output -raw postgres_admin_login
$env:PGPASSWORD = az keyvault secret show --vault-name $kv -n postgres-admin-password --query value -o tsv
$owner = az keyvault secret show --vault-name $kv -n db-owner-password --query value -o tsv
$app   = az keyvault secret show --vault-name $kv -n db-app-password --query value -o tsv

psql -v "owner_password=$owner" -v "app_password=$app" -f ..\..\..\scripts\setup-db-azure.sql `
     "host=$fqdn port=5432 dbname=postgres user=$admin sslmode=require"

Remove-Item Env:PGPASSWORD
Remove-Variable owner, app
terraform apply
```

Las opciones `-v` y `-f` van antes de la cadena de conexión. En Windows, `psql` deja de leer opciones después del primer argumento posicional: con el orden inverso ignora el script y puede imprimir las contraseñas en la salida.

El script muestra dos comprobaciones. La primera debe dar `f` en `rolsuper` y `rolbypassrls` para los dos roles: si `callejon9_app` pudiera evadir RLS, el aislamiento entre restaurantes sería decorativo. La segunda debe mostrar a `callejon9_owner` como dueño del esquema `public`. En Azure ese esquema pertenece a `azure_pg_admin`, y sin cambiar el dueño Flyway no puede crear tablas.

### 6. Desplegar el backend

```powershell
cd ..\..\..\backend
.\mvnw.cmd -B -DskipTests package
az webapp deploy --resource-group <resource_group_name> --name <web_app_name> --src-path target\backend-0.0.1-SNAPSHOT.jar --type jar
```

Flyway aplica las migraciones al arrancar, como `callejon9_owner`. El arranque tarda decenas de segundos; el log en vivo se sigue con `az webapp log tail`.

### 7. Verificación

```powershell
$api = terraform output -raw api_url
curl.exe -i "$api/actuator/health"
curl.exe -i "$api/actuator/health/liveness"
curl.exe -i "$api/actuator/health/readiness"
curl.exe -i -X POST "$api/api/v1/auth/login" -H "Content-Type: application/json" -d '{\"slug\":\"no-existe\",\"email\":\"nadie@example.com\",\"password\":\"x\"}'
```

Los tres primeros deben responder `200` con `{"status":"UP"}`, y el login, `401` sin cuerpo. La vida es la que consulta el health check de App Service; la disponibilidad confirma que la Web App llega a PostgreSQL. Un `200` con HTML ("Your app service is up and running") es la página de bienvenida de App Service, no la API: el JAR todavía no arranca o falló, y la causa está en el log (`az webapp log tail`).

Después, `scripts/verify-rls.sql` como `callejon9_app` confirma el aislamiento contra la base real. Necesita la regla temporal de firewall del paso 5: ábrela de nuevo con `operator_ip_address` y ciérrala al terminar.

### 8. Entregar el origen al frontend

El frontend de `infra/terraform/azure-frontend` recibe este valor como `backend_origin`. Cualquier otra plataforma debe ejecutar el servidor de Next.js (no sirve un hosting estático) con:

```
BACKEND_ORIGIN=<api_url>
```

El navegador sigue llamando rutas relativas `/api/v1/*` y Next las reenvía a esta API desde su servidor. Por eso el backend no necesita CORS y las cookies `SameSite=Strict` siguen siendo de primera parte. Si el navegador llamara directo a `azurewebsites.net`, el login dejaría de funcionar.

`frontend_origin` (y con él `APP_ALLOWED_ORIGINS`) debe ser exactamente el dominio público del frontend.

## Sumar a otro operador

El entorno vive en la suscripción de quien lo creó. Otra persona puede administrarlo con su propia cuenta, sin tener crédito propio: todo lo que cree, cambie o encienda se cobra a esa suscripción.

### Lo que hace el dueño de la suscripción

La cuenta nueva debe estar en el mismo directorio de Entra ID. Una cuenta de otra organización se invita antes como invitado. Hacen falta tres roles: el de la suscripción no da acceso a los datos del vault ni del estado de Terraform.

| Rol | Alcance | Sin él |
|---|---|---|
| Owner | Suscripción | No puede crear ni modificar recursos |
| Key Vault Secrets Officer | Vault del backend | `terraform plan` falla con 403 al leer los secretos |
| Storage Blob Data Contributor | Storage account del estado | `terraform init` falla con 403 al leer el estado |

En lugar de Owner, Contributor alcanza para los cambios del día a día. Solo falla en los cambios que crean o borran asignaciones de rol, como recrear la Web App o destruir el entorno. Owner, en cambio, también le permite quitar el acceso a los demás, incluido el dueño.

Desde esta carpeta, con el backend ya desplegado:

```powershell
$user = "<correo-del-operador>"
$sub  = "/subscriptions/" + (az account show --query id -o tsv)
$kv   = az keyvault show -n (terraform output -raw key_vault_name) --query id -o tsv
$st   = az storage account show -g rg-callejon9-tfstate -n <storage_account> --query id -o tsv

az role assignment create --assignee $user --role "Owner" --scope $sub
az role assignment create --assignee $user --role "Key Vault Secrets Officer" --scope $kv
az role assignment create --assignee $user --role "Storage Blob Data Contributor" --scope $st
az role assignment list --assignee $user --all -o table
```

`<storage_account>` es el de `backend.hcl`. Después, entrega `backend.hcl` y `terraform.tfvars` por un canal privado: están ignorados por git y Terraform no funciona sin ellos.

Para retirar el acceso:

```powershell
az role assignment list --assignee $user --all --query "[].id" -o tsv |
    ForEach-Object { az role assignment delete --ids $_ }
```

### Lo que hace el operador nuevo

1. Instala Terraform 1.9 o superior, Azure CLI y JDK 21. `psql` solo hace falta para el [paso 5](#5-roles-de-la-base-una-sola-vez).
2. Inicia sesión y elige la suscripción **por ID**: cada cuenta de Azure for Students tiene su propia suscripción con el mismo nombre, y con la equivocada Terraform calcularía otros nombres de recursos.

   ```powershell
   az login
   az account set --subscription <id-de-la-suscripcion-del-backend>
   $env:ARM_SUBSCRIPTION_ID = az account show --query id -o tsv
   ```

3. Copia en esta carpeta el `backend.hcl` y el `terraform.tfvars` que recibió.
4. Comprueba el acceso:

   ```powershell
   terraform init "-backend-config=backend.hcl"
   terraform plan
   ```

   Con el backend encendido, el plan no debe mostrar cambios. Con el backend apagado muestra el plan en F1, como se explica en [Apagar y encender el backend](#apagar-y-encender-el-backend). Un 403 justo después de recibir los roles es la propagación: espera unos minutos y repite.

### Reglas al compartir el entorno

- **No cambies `key_vault_admin_object_id` en `terraform.tfvars`.** Admite una sola persona: si pones tu propio ID, el siguiente `apply` borra el rol del dueño sobre el vault y crea el tuyo. Lo mismo pasa con `state_admin_object_id` del bootstrap. El acceso de cada operador se da con los comandos de arriba, no con estas variables.
- **Un `apply` a la vez.** El estado se bloquea durante cada `apply`, así que el segundo falla. Cada `apply` que cambia variables de la aplicación reinicia la API durante unos 2 minutos: avisa al equipo antes.
- **El crédito es uno solo.** Si se agota, Azure deshabilita la suscripción y la API se cae para todos. Las alertas de presupuesto solo llegan a los correos de `budget_contact_emails`.
- **`psql` solo entra desde la IP de `operator_ip_address`.** Para usarlo, aplica con tu IP como en el [paso 5](#5-roles-de-la-base-una-sola-vez) y vuelve a cerrar el firewall al terminar.

## Apagar y encender el backend

Para no gastar crédito cuando nadie usa la API:

```powershell
.\scripts\azure-backend-power.ps1 -Action Status   # muestra si está encendido o apagado
.\scripts\azure-backend-power.ps1 -Action Stop     # apaga
.\scripts\azure-backend-power.ps1 -Action Start    # vuelve a encender
```

Se corre desde la raíz del repositorio, con `az login` hecho y la suscripción Azure for Students activa (`az account show`). No necesita Terraform.

| Recurso | `Stop` | `Start` |
|---|---|---|
| Web App | La detiene | La arranca y espera a que `/actuator/health` responda `UP`, unos 2 minutos |
| Plan de App Service | Desactiva Always On, que F1 no admite, y lo baja a `F1` (gratuito) | Lo sube a `B1` (parámetro `-Sku`) y reactiva Always On |
| PostgreSQL | Lo detiene | Lo enciende; tarda unos minutos |
| Key Vault, Log Analytics, estado de Terraform | Sin cambios: cuestan centavos al mes | Sin cambios |

- **No borra nada.** La base, los secretos y la configuración se conservan.
- **Lo que se ahorra.** Casi todo el ahorro viene del plan: unos 0.45 USD por día. Mientras dure el beneficio de 12 meses, PostgreSQL B1ms no cuesta, así que detenerlo solo evita consumir las 750 horas gratuitas.
- **PostgreSQL se enciende solo.** Azure lo vuelve a encender a los 7 días. El plan sigue en F1 y la Web App detenida, así que el costo sigue bajo; si pasa una semana, basta con correr `Stop` otra vez.
- **Mientras está apagado, la API no responde.** Cualquier frontend apuntado a ella deja de funcionar.
- **Terraform ve diferencias.** Un `terraform plan` muestra el plan en `F1` y Always On desactivado. Es esperado: corre `Start` antes de cualquier `terraform apply`. Si se aplica estando apagado, Terraform devuelve el plan a `B1` y reactiva Always On, pero la Web App y PostgreSQL siguen detenidos.

## Trabajar contra el backend de Azure

El desarrollo diario no necesita Azure. El arranque local de `README.md` (PostgreSQL 16 con `scripts/setup-db.sql`, backend en `localhost:8080` y `pnpm dev`) funciona igual, y los valores por defecto siguen apuntando a `localhost`. Esta carpeta y `scripts/setup-db-azure.sql` solo se usan para administrar el entorno de Azure.

### Frontend local contra la API de Azure

Con el backend encendido (`-Action Status`):

```powershell
cd frontend
$env:BACKEND_ORIGIN = "<api_url>"   # la URL que muestra -Action Status
pnpm dev
```

Abre `http://localhost:3000`. El navegador sigue llamando `/api/v1/*` en `localhost`, y Next reenvía las peticiones a Azure. Las cookies `Secure` funcionan porque los navegadores tratan `localhost` como contexto seguro. `BACKEND_ORIGIN` se lee al iniciar `pnpm dev` (o al compilar con `pnpm build`): si cambias su valor, reinicia el servidor.

- **Es la base de producción real.** Lo que se cree desde ahí queda guardado. Para desarrollar y probar, usa la base local.
- **Cada quien crea su restaurante de prueba.** Se hace desde el alta (`POST /api/v1/signup`), que admite 5 altas por hora por IP. No se comparte la cuenta del super-administrador.

### Contrato de la API

Las rutas cuelgan de `/api/v1` y su catálogo, del lado del frontend, está en `frontend/src/lib/endpoints.ts`. El perfil `prod` apaga OpenAPI y Swagger UI; la variable `swagger_enabled` los vuelve a encender en Azure:

| Dónde | Swagger UI | JSON de OpenAPI |
|---|---|---|
| Local | `http://localhost:8080/swagger-ui.html` | `http://localhost:8080/v3/api-docs` |
| Azure, con `swagger_enabled = true` | `<api_url>/swagger-ui.html` (`terraform output swagger_url`) | `<api_url>/v3/api-docs` |

Para encenderlo, pon `swagger_enabled = true` en `terraform.tfvars` y corre `terraform apply` con el backend encendido (ver [Apagar y encender el backend](#apagar-y-encender-el-backend)). El apply agrega `SPRINGDOC_APIDOCS_ENABLED` y `SPRINGDOC_SWAGGERUI_ENABLED`, que tienen prioridad sobre `application-prod.yml`, y App Service reinicia la Web App. Para apagarlo, vuelve a `false` y aplica de nuevo.

En el Swagger de Azure, "Try it out" funciona: Swagger UI vive en el mismo origen que la API, así que el login deja las cookies ahí y las demás rutas las envían solas. Esa sesión es independiente de la del frontend. Lo que se cree desde ahí también queda en la base de producción. Necesita un JAR en el que `OpenApiConfig` declare el servidor relativo `/`. Con uno anterior, Swagger UI apunta a `http://` y el navegador bloquea las peticiones: vuelve a desplegar con el [paso 6](#6-desplegar-el-backend).

Encenderlo publica el catálogo de rutas y esquemas. No abre ninguna ruta: cada una sigue exigiendo sesión y rol, y el mismo contrato ya es público en `docs/api/openapi.json`.

Un frontend que consuma esta API debe tener en cuenta lo siguiente:

- **La sesión viaja en cookies.** El login no devuelve un token: la sesión queda en cookies `httpOnly` con `SameSite=Strict`. No hay header `Authorization: Bearer`, y el token no se guarda en JavaScript.
- **El navegador nunca llama a `azurewebsites.net`.** Llama rutas relativas `/api/v1/*`, y su propio servidor las reenvía al backend, como hacen los rewrites de `frontend/next.config.ts`. Si llamara directo a Azure, las cookies no viajarían y el login dejaría de funcionar.
- **Los errores siguen el formato RFC 7807** (`application/problem+json`).

## Mantenimiento

### Cambiar una contraseña de la base

`terraform apply -replace=random_password.db_app` (o `db_owner`) genera un valor nuevo y crea una versión nueva del secreto en Key Vault. Si el rol ya existe, aplica el mismo valor con `ALTER ROLE ... PASSWORD`, igual que en el paso 5.

La Web App guarda en caché los valores de las referencias `@Microsoft.KeyVault(...)` y los vuelve a leer cada 24 horas. Que el portal las marque como resueltas no significa que tenga la versión nueva. Para que la tome sin esperar:

```powershell
$app = terraform output -raw web_app_name
$id  = az webapp show -g (terraform output -raw resource_group_name) -n $app --query id -o tsv
az rest --method post --url "https://management.azure.com$id/config/configreferences/appsettings/refresh?api-version=2022-03-01"
az webapp restart --ids $id
```

Sin este paso, Flyway falla al arrancar con `password authentication failed for user "callejon9_owner"`.

### Si un apply se interrumpe

Si un `terraform apply` se corta a la mitad, el estado puede quedar bloqueado. Puede pasar porque se cerró la terminal o porque el proceso se quedó sin memoria.

Si `terraform force-unlock` responde `blob metadata "terraformlockid" was empty`, el bloqueo no se puede liberar por esa vía. Antes de romperlo, confirma que no hay otro `terraform` corriendo y rompe la renta del blob:

```powershell
az storage blob lease break --account-name <storage_account> -c tfstate -b azure-backend.tfstate --auth-mode login
```

Si el corte dejó en Azure algo que el estado no registra, el siguiente `apply` falla con "already exists". Ese recurso se adopta con `terraform import`. Desde Git Bash, el ID debe pasarse con `MSYS_NO_PATHCONV=1`: si no, Git Bash convierte `/subscriptions/...` en una ruta de Windows y el import falla.

## Configuración de la aplicación

| Variable | Valor | Origen |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Perfil de CAL-15 (`application-prod.yml`). Detiene el arranque si falta `JWT_SECRET`, `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD` o `APP_ALLOWED_ORIGINS`, o si alguna trae un valor de desarrollo. También fija las cookies `Secure`, expone solo `/actuator/health` y apaga OpenAPI y Swagger UI salvo con `swagger_enabled` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<fqdn>:5432/callejon9?sslmode=require` | Sustituye la URL local de `application.yml`; Flyway la reutiliza |
| `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD`, `JWT_SECRET` | Referencias `@Microsoft.KeyVault(...)` | Key Vault, leídas con la identidad de la Web App |
| `AUTH_COOKIE_SECURE` | `true` | Sin efecto mientras `prod` esté activo, porque el perfil fija `cookie-secure: true`. Se conserva para que las cookies sigan siendo `Secure` si alguien cambia el perfil |
| `APP_ALLOWED_ORIGINS` | `frontend_origin` | Orígenes del WebSocket `/ws` |
| `APP_TRUSTED_PROXIES` | `trusted_proxy_addresses`, separadas por comas | IP de salida del frontend. `ClientIp` las salta en `X-Forwarded-For` para que los límites por IP del login y del alta cuenten por navegador (CAL-169) |
| `SERVER_PORT` | `80` | Puerto en el que App Service enruta a Java SE |
| `JAVA_OPTS` | `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0` | Mismas opciones que `JAVA_TOOL_OPTIONS` en `backend/Dockerfile` |
| `SPRINGDOC_APIDOCS_ENABLED`, `SPRINGDOC_SWAGGERUI_ENABLED` | `true` | Solo con `swagger_enabled = true`. Encienden OpenAPI y Swagger UI, que `prod` apaga |

## Puerta de calidad

```powershell
.\scripts\terraform-check.ps1
```

Corre `terraform fmt -check` y `terraform validate` en todos los roots, incluidos los de este módulo, sin backend ni credenciales.

## Pendientes antes de exponer la API

- **Referencias de Key Vault resueltas.** Si App Service no logra resolver una referencia `@Microsoft.KeyVault(...)`, entrega como valor el texto de la referencia. El guard de `prod` no lo detecta: ese texto no está vacío, supera los 32 bytes y no es un valor publicado. Tras el primer despliegue, comprueba en el portal (Web App → Variables de entorno) que `JWT_SECRET`, `DB_APP_PASSWORD` y `DB_OWNER_PASSWORD` aparecen como resueltas. En el despliegue del 2026-10-16 las tres aparecieron como `Resolved`. Después de cambiar un secreto, sigue [Cambiar una contraseña de la base](#cambiar-una-contraseña-de-la-base).
- **`ClientIp` detrás de dos proxies (CAL-169).** La SIT del 2026-10-21 confirmó que los límites por IP eran globales: 20 logins fallidos de cualquiera bloqueaban el login de todos. App Service entrega al contenedor desde una dirección de enlace local (169.254.x.x) en la que `ClientIp` no confiaba, y lo que llega por la web trae al final la IP de salida del frontend. `ClientIp` ya confía en el enlace local y salta las direcciones de `trusted_proxy_addresses`. Tras desplegar, llena esa variable con `terraform output outbound_ip_addresses` de `azure-frontend` y repite las fases A a C de la SIT CAL-133 C05, por el frontend y directo a la API.
- **Contraseña del super-administrador.** La de `V6__seed_platform_admin.sql` está publicada en el README; se cambia tras el primer despliegue, con una migración nueva o desde la aplicación, nunca reescribiendo `V6`. En el despliegue del 2026-10-16 se cambió directamente en la base, y la contraseña nueva está en Key Vault como `platform-admin-password`, un secreto creado fuera de Terraform. `V6` la sigue sembrando en cualquier base nueva, así que el pendiente sigue abierto para los demás ambientes.
- **Valores verificados en el primer despliegue (2026-10-16):** el stack `JAVA|21-java21` que genera `application_stack` y el puerto 80 de Java SE, que Tomcat abrió en el arranque.

## Riesgos aceptados

- **PostgreSQL con endpoint público.** Lo protegen el firewall (solo las IP de salida de la Web App y, temporalmente, la del operador), TLS obligatorio y contraseñas aleatorias de 32 caracteres. El acceso privado exige integrar la Web App a una VNet y deja al operador sin acceso para el bootstrap; se revisa si la base llega a contener datos reales.
- **Key Vault con acceso de red público.** El acceso se controla solo por RBAC, como en `azure-ci`.
- **Secretos en el estado de Terraform.** `random_password` los guarda en el estado, que vive en un storage account privado sin claves compartidas y con acceso solo por Entra ID.
- **`sslmode=require` sin verificar el certificado.** Cifra la conexión, pero no valida el nombre del servidor. `verify-full` necesita la CA raíz de Azure en el truststore de la JVM.
