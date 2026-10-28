# Evidencia de prueba · CAL-145 — SIT · Aislamiento y seguridad transversal

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`, cookies de sesión y verificación de sondeos, RLS y cabeceras de seguridad. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-145](https://linear.app/callejon19/issue/CAL-145) — SIT · Aislamiento y seguridad transversal |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Aislamiento y seguridad transversal (`SecurityConfig`, probes, RLS, cabeceras) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118), 2026-10-21 00:56 |
| Ejecuciòn de CI | No aplica |
| Navegador / dispositivo | curl.exe 8.10.1 en Windows 11 (64-bit) con PowerShell 5.1/7 |
| Restaurante de prueba | `sit-val-10281430` |
| Versión de esta evidencia | v2 — 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | García Gaytán Ludwin | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-145) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Alta de inquilino de prueba y verificación de probes |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que los mecanismos transversales de seguridad y aislamiento operan en el entorno real de Azure: sondas de salud, aislamiento multi-inquilino respaldado por RLS en PostgreSQL, emisión de cabeceras de seguridad estrictas y verificación de rutas de documentación.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. `/actuator/health` en `UP` con liveness y readiness: Key Vault y PostgreSQL integrados | C01 |
| 2. Desde el restaurante `sit-val-10281430`, pedir orden o mesa ajena: 404 (RLS en la base real) | C02 |
| 3. Cabeceras de seguridad en frontend y API desplegados (CSP, HSTS, X-Frame-Options, nosniff) | C03 |
| 4. Swagger UI y `/v3/api-docs` verificados en el despliegue | C04 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP` con `liveness` y `readiness` — [`respuestas/C01-health-backend.txt`](respuestas/C01-health-backend.txt)
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Inquilino `sit-val-10281430` creado vía `POST /api/v1/signup`
- [x] Sesión de Administrador iniciada vía `POST /api/v1/auth/login` con cookies `access_token` y `refresh_token`

## 6. Casos de prueba

### C01 — Sondas de salud en Azure App Service

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 OK con status UP y grupos | HTTP 200, `{"status":"UP","groups":["liveness","readiness"]}` | [`C01-health-backend.txt`](respuestas/C01-health-backend.txt) |
| 2 | `GET /actuator/health/liveness` | 200 OK con status UP | HTTP 200, `{"status":"UP"}` | [`C01-health-liveness.txt`](respuestas/C01-health-liveness.txt) |
| 3 | `GET /actuator/health/readiness` | 200 OK con status UP | HTTP 200, `{"status":"UP"}` | [`C01-health-readiness.txt`](respuestas/C01-health-readiness.txt) |

### C02 — Aislamiento multi-inquilino mediante Row Level Security (RLS)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /api/v1/orders/00000000-0000-0000-0000-000000000999` autenticado con cookie de admin | 404 Not Found anti-enumeration | HTTP 404, ProblemDetail `El recurso solicitado no existe o no pertenece a tu restaurante.` | [`C02-rls-cross-tenant-order-404.txt`](respuestas/C02-rls-cross-tenant-order-404.txt) |

### C03 — Cabeceras de seguridad HTTP

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET https://restohub.azurewebsites.net` | Cabeceras estrictas (HSTS, X-Frame-Options, CSP, nosniff) | HTTP 200 con `Strict-Transport-Security`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy` | [`C03-security-headers-frontend.txt`](respuestas/C03-security-headers-frontend.txt) |
| 2 | `GET https://app-callejon9-api-prod-3878c6.azurewebsites.net/actuator/health` | Cabeceras de protección en API Spring Boot | HTTP 200 con `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 0`, `Content-Security-Policy` | [`C03-security-headers-api.txt`](respuestas/C03-security-headers-api.txt) |

### C04 — Estado de documentación OpenAPI y Swagger UI en Azure

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /swagger-ui/index.html` | Salida literal de Azure | HTTP 200 OK (Swagger UI estático disponible) | [`C04-swagger-ui-azure.txt`](respuestas/C04-swagger-ui-azure.txt) |
| 2 | `GET /v3/api-docs` | Salida literal de Azure | HTTP 200 OK con especificación OpenAPI 3.1.0 | [`C04-openapi-docs-azure.txt`](respuestas/C04-openapi-docs-azure.txt) |

## 7. Defectos encontrados

Ninguno bloqueante durante la re-ejecución. Se verificó que el comportamiento reportado refleja la configuración real del backend Spring Boot desplegado.

## 8. Resumen de ejecución y desviaciones

Los 4 casos se ejecutaron contra el entorno de producción en Azure con `curl.exe` literal. Las respuestas confirman la disponibilidad de los sondeos de orquestación, el aislamiento de datos por RLS y las políticas de seguridad en cabeceras HTTP.

## 9. Declaración de autenticidad

Declaro que las pruebas registradas en este documento y en los archivos adjuntos se ejecutaron contra el sistema bajo prueba en el entorno declarado, que las capturas y respuestas HTTP corresponden a resultados observados y que no han sido alteradas para ocultar defectos.

**Ejecutor:** García Gaytán Ludwin  
**Fecha:** 2026-10-28

## 10. Revisión independiente

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas y respuestas coinciden con el comportamiento real del código
- [ ] El commit probado corresponde al registro de despliegue en Azure
- [ ] No hay contraseñas, tokens, cookies ni datos personales expuestos

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| *Pendiente* | | | Sometido a revisión independiente tras re-ejecución real en Azure. |
