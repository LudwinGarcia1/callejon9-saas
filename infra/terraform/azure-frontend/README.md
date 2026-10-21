# Frontend de Callejón 9 en Azure

Publica el servidor Next.js en Azure App Service. El navegador solo habla con esta Web App: llama rutas relativas `/api/v1/*` y Next las reenvía, del lado del servidor, a la API de `infra/terraform/azure-backend`. El backend no necesita CORS y las cookies `SameSite=Strict` siguen siendo de primera parte.

Vive en una suscripción **distinta** a la del backend (Azure for Students de otro integrante), con su propio estado de Terraform. Las dos se unen por la URL pública de la API.

## Arquitectura

```
Navegador ──HTTPS──► App Service F1 Linux · Node 24 · Next.js standalone   (suscripción del frontend)
                       │  rewrite /api/v1/* compilado con BACKEND_ORIGIN
                       ▼ HTTPS
                     API Spring Boot en App Service                         (suscripción del backend)
```

| Recurso | Nombre | Para qué |
|---|---|---|
| Resource group | `rg-callejon9-web-<env>-<región>-001` | Contenedor del frontend |
| App Service plan | `asp-callejon9-web-<env>-<región>-001` | `F1` por defecto (gratis). `B1` con `app_service_sku` |
| Web App | `restohub` → `https://restohub.azurewebsites.net` (variable `web_app_name`) | Node 24 LTS, HTTPS obligatorio, TLS 1.2, FTP y publicación con contraseña desactivados. Arranca con `node server.js` |
| Log Analytics y Application Insights | `log-…`, `appi-…` | Opcionales (`monitoring_enabled`, apagado por defecto), con tope de 0.1 GB diarios |

**Regiones.** La política de la suscripción Azure for Students solo permite `westus`, `eastus`, `eastus2`, `centralus` y `westus3`; `mexicocentral` no está en la lista. Se usa `westus3`.

**Sin secretos.** El frontend no guarda ninguno: la sesión es la cookie que emite el backend, y `BACKEND_ORIGIN` es una URL pública. Por eso no hay Key Vault.

## Costo y Límites de App Service F1 (Free)

| Plan | USD al mes | Notas y Límites Críticos |
|---|---|---|
| `F1` (por defecto) | 0 | **Arranque en frío:** se descarga tras ~20 minutos sin tráfico (primera visita tarda 30-50s).<br>**WPStopRequests = 15/día:** cada deploy, restart o reciclado consume cupo; superarlo produce HTTP 403 QuotaExceeded hasta el reset diario (04:00 UTC).<br>**CPU y Tráfico:** 60 min de CPU/día y 165 MB de salida/día (BytesSent). Sin Always On ni health check. |
| `B1` | ≈ 13 | Always On, sin límite de reinicios diarios, CPU dedicada y health check en `/login`. |

### Mitigación y Procedimiento para Demos y Evaluaciones (UAT)
Para evitar caídas de cuota (`QuotaExceeded`) o retrasos por arranque en frío durante evaluaciones con usuarios finales (UAT) o presentaciones:
1. **Subir temporalmente a B1:**
   En `infra/terraform/azure-frontend/terraform.tfvars`: `app_service_sku = "B1"` y ejecutar `terraform apply`.
2. **Consultar el consumo de cuota actual en F1:**
   `az rest --method get --uri /subscriptions/{subId}/resourceGroups/{rg}/providers/Microsoft.Web/serverfarms/{plan}/usages?api-version=2022-03-01 --query "value[].{name:name.localizedValue, current:currentValue, limit:limit}" -o table`
3. **Regresar a F1 tras concluir la sesión:**
   Revertir `app_service_sku = "F1"` y ejecutar `terraform apply`.
El estado de Terraform cuesta centavos.

## Requisitos

- Terraform 1.9 o superior y Azure CLI con sesión en la suscripción del frontend.
- Docker en ejecución: el paquete se compila en Linux.
- La URL de la API (`api_url` de `azure-backend`).

## Procedimiento

Comandos de PowerShell desde `infra/terraform/azure-frontend`, salvo que se indique otra ruta.

### 1. Sesión

```powershell
az login
$env:ARM_SUBSCRIPTION_ID = az account show --query id -o tsv
$operator = az ad signed-in-user show --query id -o tsv
```

### 2. Estado remoto (una sola vez por suscripción)

Reutiliza el bootstrap del backend, con una región permitida. Su estado local se guarda fuera del repositorio:

```powershell
$state = "$env:USERPROFILE\.callejon9-terraform"
New-Item -ItemType Directory -Force $state | Out-Null
$env:TF_DATA_DIR = "$state\frontend-bootstrap-data"
terraform -chdir=..\azure-backend\bootstrap init
terraform -chdir=..\azure-backend\bootstrap apply -state="$state\frontend-bootstrap.tfstate" -var "location=westus3" -var "state_admin_object_id=$operator"
Remove-Item Env:TF_DATA_DIR
```

### 3. Variables

Copia `backend.hcl.example` a `backend.hcl` con el storage account que imprimió el bootstrap, y `terraform.tfvars.example` a `terraform.tfvars` con la URL de la API. Ambos están ignorados por git.

### 4. Infraestructura

```powershell
terraform init "-backend-config=backend.hcl"
terraform apply
```

### 5. Desplegar

Desde la raíz del repositorio:

```powershell
.\scripts\deploy-frontend-azure.ps1
```

El script compila con la etapa `builder` de `frontend/Dockerfile` y luego reinstala con `node_modules` plano (`node-linker=hoisted`) y recompila. Así la salida `standalone` no trae enlaces simbólicos de pnpm, que el despliegue zip de App Service no conserva. Después la sube con `az webapp deploy` y espera a que `/login` responda 200 con la CSP de Next.

`BACKEND_ORIGIN` queda fijo en el rewrite al compilar. Si cambia la API, vuelve a correr el script; cambiar solo la app setting no basta.

### 6. Avisar al backend

En la suscripción del backend, `frontend_origin` de `azure-backend` debe ser la URL de este frontend:

```powershell
terraform output -raw frontend_url
```

Llega al backend como `APP_ALLOWED_ORIGINS`, la lista de orígenes del WebSocket `/ws`.

## Verificación

Con el backend encendido (`.\scripts\azure-backend-power.ps1 -Action Status`):

```powershell
$web = terraform output -raw frontend_url
curl.exe -I "$web/login"              # 200, con Content-Security-Policy y Strict-Transport-Security
curl.exe -I "$web/waiter"             # 307 a /login?next=%2Fwaiter
curl.exe -i -X POST "$web/api/v1/auth/login" -H "Content-Type: application/json" -d '{\"slug\":\"no-existe\",\"email\":\"nadie@example.com\",\"password\":\"x\"}'
```

El último debe responder `401`, que viene del backend a través del proxy de Next. Si el backend está apagado, el proxy devuelve `403 Site Disabled`.

## Riesgos y pendientes

- **Límites por IP del backend.** Todas las peticiones llegan a la API desde las IP de salida de esta Web App. Ver el pendiente «`ClientIp` detrás de dos proxies» en `../azure-backend/README.md`: hay que verificarlo con este frontend ya publicado.
- **Arranque en frío con `F1`.** La primera visita tras un periodo inactivo tarda varios segundos. Para una demostración, abre la página unos minutos antes o sube a `B1`.
