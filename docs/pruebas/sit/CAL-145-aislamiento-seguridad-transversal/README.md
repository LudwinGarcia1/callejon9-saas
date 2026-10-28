# Evidencia de prueba · CAL-145 — SIT · Aislamiento y seguridad transversal

> **v1.** C01, C02, C03 y C04 ejecutados y verificados contra el entorno de producción en Azure. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-145](https://linear.app/callejon19/issue/CAL-145) — SIT · Aislamiento y seguridad transversal |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Aislamiento y seguridad transversal |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Laptop de desarrollo y terminal de pruebas con Google Chrome 155 (64-bit) y cURL |
| Restaurantes involucrados | `pruebas` (tenant principal de prueba) y `sit-alta-10211930` (tenant secundario para aislamiento) |
| Versión de esta evidencia | v1 · 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Verificación de sondas, cabeceras, RLS y Swagger |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-145) | 2026-10-21 | 2026-10-26 | 2026-10-26 21:00 | 2026-10-26 21:45 | Ejecución formal de verificación de seguridad |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Demostrar que los mecanismos transversales de seguridad y aislamiento operan en el entorno real de Azure:
1. Las sondas de salud (`liveness` y `readiness`) responden `UP` demostrando integración con PostgreSQL y Key Vault sin exponer componentes internos.
2. PostgreSQL impone aislamiento de datos entre restaurantes mediante Row Level Security (RLS) forzado: un inquilino no puede acceder a órdenes ni mesas de otro inquilino (`404 Not Found`).
3. El frontend y la API emiten cabeceras de seguridad estrictas (`CSP` con nonce, `HSTS`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`).
4. Swagger UI y la documentación OpenAPI están deshabilitados en producción (`swagger_enabled = false`).

---

## 5. Casos de Prueba Ejecutados

### C01 — Sondas de Salud e Integración (`/actuator/health`, `liveness`, `readiness`)
* **Propósito:** Validar que el backend responde saludablemente, confirmando conectividad con Azure Key Vault y PostgreSQL.
* **Comandos y URLs:**
  * `GET https://app-callejon9-api-prod-3878c6.azurewebsites.net/actuator/health/liveness`
  * `GET https://app-callejon9-api-prod-3878c6.azurewebsites.net/actuator/health/readiness`
* **Resultado observado:**
  * `liveness`: `HTTP/1.1 200 OK`, `{"status":"UP"}`. Solo expone el estado, sin detalles internos conforme a `docs/operacion-probes.md`.
  * `readiness`: `HTTP/1.1 200 OK`, `{"status":"UP"}`. Confirma que PostgreSQL está listo para aceptar tráfico.
* **Evidencia:** `respuestas/C01-health-liveness-readiness.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Aislamiento Multi-inquilino mediante Row Level Security (RLS)
* **Propósito:** Demostrar que una petición autenticada desde el restaurante `pruebas` no puede leer datos pertenecientes a `sit-alta-10211930`.
* **Pasos:**
  1. Iniciar sesión como mesero o administrador del restaurante `pruebas`.
  2. Obtener el token de sesión JWT firmado con `tenant_id` de `pruebas`.
  3. Ejecutar `GET /api/v1/orders/{orderId-de-sit-alta}` intentando consultar la orden `ord-20261021-001` perteneciente al otro inquilino.
  4. Ejecutar `GET /api/v1/tables/{tableId-de-sit-alta}` intentando consultar una mesa del otro inquilino.
* **Resultado observado:**
  * En ambos intentos, la respuesta fue `HTTP/1.1 404 Not Found` con cuerpo de error RFC 7807 (`ProblemDetail`).
  * En la base de datos PostgreSQL, la consulta SQL ejecutada bajo `app.tenant_id = 'tenant-pruebas'` devolvió 0 filas gracias a `FORCE ROW LEVEL SECURITY`. El backend no encuentra el recurso y responde `404` sin revelar que la entidad existe en otro inquilino.
* **Evidencia:** `respuestas/C02-rls-cross-tenant-isolation.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C03 — Cabeceras de Seguridad HTTP en Frontend y API
* **Propósito:** Verificar que las respuestas HTTP de `https://restohub.azurewebsites.net` y de la API aplican las directivas de seguridad especificadas en `docs/cabeceras-seguridad.md`.
* **Cabeceras verificadas:**
  * `Strict-Transport-Security: max-age=31536000` (HSTS activo en producción).
  * `X-Frame-Options: DENY` (Mitigación total contra Clickjacking).
  * `X-Content-Type-Options: nosniff` (Prevención de MIME-sniffing).
  * `Referrer-Policy: strict-origin-when-cross-origin`.
  * `Content-Security-Policy: default-src 'self'; script-src 'self' 'nonce-...' ...; object-src 'none'; frame-ancestors 'none'`.
* **Resultado observado:** Ambas capas emiten las cabeceras requeridas. El frontend genera un nonce criptográfico de 128 bits por petición para los scripts de Next.js.
* **Evidencia:** `respuestas/C03-security-headers-frontend-api.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C04 — Swagger UI y Documentación OpenAPI Apagados en Producción
* **Propósito:** Comprobar que las rutas de exploración y documentación de la API no se encuentran expuestas públicamente en el entorno de producción.
* **Peticiones:**
  * `GET https://app-callejon9-api-prod-3878c6.azurewebsites.net/swagger-ui.html`
  * `GET https://app-callejon9-api-prod-3878c6.azurewebsites.net/v3/api-docs`
* **Resultado observado:**
  * `/swagger-ui.html` responde `HTTP/1.1 404 Not Found`.
  * `/v3/api-docs` responde `HTTP/1.1 404 Not Found`.
  * La variable `swagger_enabled` permanece en `false` en la configuración productiva de Azure App Service, cerrando vectores de reconocimiento para actores maliciosos.
* **Evidencia:** `respuestas/C04-swagger-docs-production-disabled.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Sondas de salud `/actuator/health` (liveness y readiness en UP) | ✅ **Aprobado** | Ninguno |
| **C02** | Aislamiento estricto multi-tenant (RLS en PostgreSQL real) | ✅ **Aprobado** | Ninguno |
| **C03** | Cabeceras de seguridad HTTP (HSTS, CSP con nonce, no-sniff, DENY) | ✅ **Aprobado** | Ninguno |
| **C04** | Swagger UI y OpenAPI docs apagados en producción | ✅ **Aprobado** | Ninguno |

**Conclusión del ejecutor:** Los 4 criterios de `CAL-145` se cumplen en el entorno de producción de Azure. La plataforma RestoHub demuestra aislamiento nativo a nivel de base de datos, protección de cabeceras en el navegador y control de endpoints de diagnóstico.

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Las capturas y respuestas coinciden con el comportamiento real del código
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Evidencias HTTP coherentes con la configuración de Azure y las invariantes de RLS. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución y verificación real en el entorno de Azure, y que las respuestas reflejan el estado del sistema en producción.

**Ejecutor:** García Gaytán Ludwin · **Fecha:** 2026-10-26
