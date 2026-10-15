# Backend de Callejón 9 en Azure

Infraestructura para publicar la API de Spring Boot en Azure. El frontend se aloja en otra plataforma y consume esta API a través de su propio servidor Next.js. Se aprovisiona solo con Terraform, en la suscripción Azure for Students del equipo, y es independiente de `infra/terraform/azure-ci` (otra suscripción y otro estado).

## Arquitectura

```
Navegador ──HTTPS──► Frontend Next.js (otra plataforma)
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

El plan B1 se cobra mientras exista, aunque la Web App esté detenida. Para dejar de pagar hay que destruirlo (`terraform destroy`) o bajarlo a `F1`. Un servidor PostgreSQL detenido deja de cobrar cómputo, pero Azure lo vuelve a encender solo a los 7 días.

## Requisitos

- Terraform 1.9 o superior, Azure CLI y `psql`.
- Una cuenta con permiso para crear asignaciones de rol en la suscripción (Owner).
- JDK 21 para compilar el JAR.

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

Copia `backend.hcl.example` a `backend.hcl` con el storage account que imprimió el bootstrap, y `terraform.tfvars.example` a `terraform.tfvars` con el origen público del frontend y el valor de `$operator`. Ambos archivos están ignorados por git.

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

psql "host=$fqdn port=5432 dbname=postgres user=$admin sslmode=require" `
     -v "owner_password=$owner" -v "app_password=$app" -f ..\..\..\scripts\setup-db-azure.sql

Remove-Item Env:PGPASSWORD
terraform apply
```

La última consulta del script debe mostrar `f` en `rolsuper` y `rolbypassrls` para los dos roles. Si `callejon9_app` pudiera evadir RLS, el aislamiento entre restaurantes sería decorativo.

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

Los tres primeros deben responder `200` con `{"status":"UP"}`, y el login, `401` sin cuerpo. La vida es la que consulta el health check de App Service; la disponibilidad confirma que la Web App llega a PostgreSQL. Después, `scripts/verify-rls.sql` como `callejon9_app` (con la regla temporal de firewall) confirma el aislamiento contra la base real.

### 8. Entregar el origen al frontend

La plataforma del frontend debe ejecutar el servidor de Next.js (Vercel, Netlify o Render; no sirve un hosting estático) con:

```
BACKEND_ORIGIN=<api_url>
```

El navegador sigue llamando rutas relativas `/api/v1/*` y Next las reenvía a esta API desde su servidor. Por eso el backend no necesita CORS y las cookies `SameSite=Strict` siguen siendo de primera parte. Si el navegador llamara directo a `azurewebsites.net`, el login dejaría de funcionar.

`frontend_origin` (y con él `APP_ALLOWED_ORIGINS`) debe ser exactamente el dominio público del frontend.

## Configuración de la aplicación

| Variable | Valor | Origen |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Perfil de CAL-15 (`application-prod.yml`). Detiene el arranque si falta `JWT_SECRET`, `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD` o `APP_ALLOWED_ORIGINS`, o si alguna trae un valor de desarrollo. También fija las cookies `Secure`, expone solo `/actuator/health` y apaga OpenAPI y Swagger UI |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<fqdn>:5432/callejon9?sslmode=require` | Sustituye la URL local de `application.yml`; Flyway la reutiliza |
| `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD`, `JWT_SECRET` | Referencias `@Microsoft.KeyVault(...)` | Key Vault, leídas con la identidad de la Web App |
| `AUTH_COOKIE_SECURE` | `true` | Sin efecto mientras `prod` esté activo, porque el perfil fija `cookie-secure: true`. Se conserva para que las cookies sigan siendo `Secure` si alguien cambia el perfil |
| `APP_ALLOWED_ORIGINS` | `frontend_origin` | Orígenes del WebSocket `/ws` |
| `SERVER_PORT` | `80` | Puerto en el que App Service enruta a Java SE |
| `JAVA_OPTS` | `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0` | Mismas opciones que `JAVA_TOOL_OPTIONS` en `backend/Dockerfile` |

## Puerta de calidad

```powershell
.\scripts\terraform-check.ps1
```

Corre `terraform fmt -check` y `terraform validate` en todos los roots, incluidos los de este módulo, sin backend ni credenciales.

## Pendientes antes de exponer la API

- **Referencias de Key Vault resueltas.** Si App Service no logra resolver una referencia `@Microsoft.KeyVault(...)`, entrega como valor el texto de la referencia. El guard de `prod` no lo detecta: ese texto no está vacío, supera los 32 bytes y no es un valor publicado. Tras el primer despliegue, comprueba en el portal (Web App → Variables de entorno) que `JWT_SECRET`, `DB_APP_PASSWORD` y `DB_OWNER_PASSWORD` aparecen como resueltas.
- **`ClientIp` detrás de dos proxies.** El servidor del frontend reenvía todas las peticiones, así que el backend las verá llegar desde unas pocas IP de esa plataforma. Los límites por IP pasarían a ser globales: 5 altas por hora y 20 logins fallidos cada 15 minutos para todos los usuarios juntos. Hay que verificarlo tras el primer despliegue y ajustar la confianza en `X-Forwarded-For`.
- **Contraseña del super-administrador.** La de `V6__seed_platform_admin.sql` está publicada en el README; se cambia tras el primer despliegue, con una migración nueva o desde la aplicación, nunca reescribiendo `V6`.
- **Valores por verificar en el primer despliegue:** el stack `JAVA|21-java21` que genera `application_stack` y el puerto 80 de Java SE.

## Riesgos aceptados

- **PostgreSQL con endpoint público.** Lo protegen el firewall (solo las IP de salida de la Web App y, temporalmente, la del operador), TLS obligatorio y contraseñas aleatorias de 32 caracteres. El acceso privado exige integrar la Web App a una VNet y deja al operador sin acceso para el bootstrap; se revisa si la base llega a contener datos reales.
- **Key Vault con acceso de red público.** El acceso se controla solo por RBAC, como en `azure-ci`.
- **Secretos en el estado de Terraform.** `random_password` los guarda en el estado, que vive en un storage account privado sin claves compartidas y con acceso solo por Entra ID.
- **`sslmode=require` sin verificar el certificado.** Cifra la conexión, pero no valida el nombre del servidor. `verify-full` necesita la CA raíz de Azure en el truststore de la JVM.
