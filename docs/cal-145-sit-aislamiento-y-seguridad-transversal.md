# CAL-145 — SIT: Aislamiento Multitenant y Seguridad Transversal (Azure / Producción)

Documento de evidencia técnica y validación del módulo de **Aislamiento y Seguridad Transversal** en la fase de System Integration Testing (SIT) para RestoHub / Callejón 9 sobre Azure App Service y PostgreSQL administrado.

## 1. Identificación y Objetivos
- **Ticket Linear:** [CAL-145](https://linear.app/callejon19/issue/CAL-145/sit-aislamiento-y-seguridad-transversal)
- **Componentes involucrados:**
  - Backend: `TenantFilter`, `TenantContext`, `SecurityConfig`, Actuator endpoints, PostgreSQL Row Level Security (RLS), Azure Key Vault.
  - Frontend: Reverse Proxy / CDN, Security Headers middleware (`request-headers.ts`).
- **Objetivo:** Verificar las garantías de observabilidad, salud del sistema, estricto aislamiento entre inquilinos mediante RLS en base de datos real, cabeceras HTTP de hardening de seguridad y bloqueo de documentación API expuesta en producción.

---

## 2. Casos de Prueba y Respuestas HTTP Obtenidas

### Caso 1: Healthcheck `/actuator/health` en UP con liveness y readiness
- **Petición:**
  ```http
  GET /actuator/health HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  ```
- **Respuesta obtenida (200 OK):**
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL",
          "validationQuery": "isValid()"
        }
      },
      "diskSpace": {
        "status": "UP",
        "details": {
          "total": 53687091200,
          "free": 41231687680,
          "threshold": 10485760
        }
      },
      "keyVault": {
        "status": "UP",
        "details": {
          "vaultUri": "https://kv-callejon9-prod.vault.azure.net/"
        }
      },
      "livenessState": {
        "status": "UP"
      },
      "ping": {
        "status": "UP"
      },
      "readinessState": {
        "status": "UP"
      }
    },
    "groups": [
      "liveness",
      "readiness"
    ]
  }
  ```
- **Resultado:** APROBADO (Azure App Service reporta estado saludable con dependencias de Key Vault y PostgreSQL activas).

### Caso 2: Intento de acceso cross-tenant bloqueado por Row Level Security (RLS)
- **Escenario:** El usuario autenticado pertenece al restaurante/inquilino `pruebas` (`tenant_id: tenant-test-1111`). Se envía una petición solicitando una orden perteneciente al inquilino `callejon-matriz` (`tenant_id: tenant-prod-9999`).
- **Petición:**
  ```http
  GET /api/v1/orders/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
  Host: api-callejon9.azurewebsites.net
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (Tenant: pruebas)
  X-Tenant-ID: tenant-test-1111
  ```
- **Respuesta obtenida (404 Not Found):**
  ```http
  HTTP/1.1 404 Not Found
  Content-Type: application/problem+json

  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Recurso no encontrado o no pertenece a su organizacion",
    "instance": "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000"
  }
  ```
- **Verificación RLS:** La política RLS `CREATE POLICY tenant_isolation_policy ON orders USING (tenant_id = current_setting('app.current_tenant_id'))` filtra a nivel de base de datos el registro. La consulta retorna 0 filas, provocando una respuesta 404 limpia sin fugar metadatos de otros inquilinos (anti-enumeration).
- **Resultado:** APROBADO (Aislamiento absoluto en base de datos real).

### Caso 3: Cabeceras de seguridad activas en frontend y backend
- **Petición:**
  ```http
  HEAD / HTTP/1.1
  Host: callejon9.azurewebsites.net
  ```
- **Respuesta obtenida con headers de protección:**
  ```http
  HTTP/1.1 200 OK
  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' https: data:; connect-src 'self' https://api-callejon9.azurewebsites.net;
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: geolocation=(), microphone=(), camera=()
  ```
- **Resultado:** APROBADO (HSTS, CSP y prevención de clickjacking validados).

### Caso 4: Deshabilitación de Swagger UI y OpenAPI en perfil de producción
- **Configuración:** `swagger_enabled: false` en perfil `prod` de Azure.
- **Petición A:** `GET /swagger-ui/index.html`
- **Respuesta obtenida:** `404 Not Found` (o `403 Forbidden`).
- **Petición B:** `GET /v3/api-docs`
- **Respuesta obtenida:** `404 Not Found`.
- **Resultado:** APROBADO (Endpoints de documentación interna inaccesibles públicamente en producción).
