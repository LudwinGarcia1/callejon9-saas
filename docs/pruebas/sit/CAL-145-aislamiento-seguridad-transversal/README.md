# Evidencia de prueba · CAL-145 — SIT · Aislamiento y seguridad transversal

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID, un dominio de errores que el código no usa, y Swagger «apagado» cuando hoy responde) y registraba una revisión que no ocurrió. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-145](https://linear.app/callejon19/issue/CAL-145/sit-aislamiento-y-seguridad-transversal) — SIT · Aislamiento y seguridad transversal |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Aislamiento y seguridad transversal (API desplegada, frontend publicado y proxy `/api/v1/*`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | `curl` 8.23.0 desde Garuda Linux, sin sesión ni credenciales |
| Restaurante de prueba | No aplica en C01, C03 y C04 (peticiones sin sesión) |
| Versión de esta evidencia | v2 · 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear (reasignado el 2026-10-28) |
| Ejecutor de la prueba | Octavio Duarte | Script de esta carpeta |
| Revisor | Braulio Hernández Mejía | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | Sigue abierta: 7 SIT se re-ejecutan |
| Esta tarea (CAL-145) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28; C02 pendiente |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | — | — | C02 necesita un segundo restaurante (sección 8) |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:22 | 2026-10-28 14:23 | Primera corrida 14:22; la de esta evidencia, 14:23, agrega `C03-api-por-http.txt` |
| Re-prueba de defectos | | | | | Defectos abiertos (sección 7) |

## 4. Alcance y criterios cubiertos

**Objetivo.** Verificar sobre el despliegue real los controles transversales: que la API está sana con sus dependencias, que un restaurante no alcanza datos de otro, que frontend y API envían las cabeceras de seguridad, y que la documentación interactiva de la API no está expuesta en producción.

**Fuera de alcance.** Pruebas de penetración, fuerza bruta (cubierta en CAL-133 C05) y el contenido de los secretos de Key Vault.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. `/actuator/health` en `UP` con liveness y readiness: Key Vault y PostgreSQL integrados. | C01 |
| 2. Desde el restaurante `pruebas`, pedir una orden o mesa de otro restaurante: 404 (RLS en la base real). | C02 — ⏭️ No ejecutado |
| 3. Cabeceras de seguridad en el frontend y la API desplegados (CSP, HSTS, X-Frame-Options, nosniff). | C03 |
| 4. Swagger UI y `/v3/api-docs` apagados en producción mientras `swagger_enabled` siga en `false`. | C04 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido — [`respuestas/C01-health.txt`](respuestas/C01-health.txt), 2026-10-28 14:23
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [ ] Segundo restaurante con datos propios para C02 — **no disponible** (sección 8)

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Peticiones | Sin cookie ni cabecera de autorización | [`comandos/sit-seguridad.sh`](comandos/sit-seguridad.sh) |

## 6. Casos de prueba

C01, C03 y C04 los ejecutó [`comandos/sit-seguridad.sh`](comandos/sit-seguridad.sh), que guarda cada respuesta literal de `curl -i` en `respuestas/` (las cabeceras `Set-Cookie`, si aparecieran, se redactan; no apareció ninguna).

### C01 — La API desplegada responde UP en health, liveness y readiness

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | Sin sesión |
| Inicio / fin de ejecución | `2026-10-28 14:23` / `2026-10-28 14:23` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** La API encendida.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `curl -i …/actuator/health` | — | 200 UP con grupos | 14:23:55 · **200** `{"status":"UP","groups":["liveness","readiness"]}` | [`C01-health.txt`](respuestas/C01-health.txt) |
| 2 | `curl -i …/actuator/health/liveness` | — | 200 UP | 14:23:56 · **200** `{"status":"UP"}` | [`C01-health-liveness.txt`](respuestas/C01-health-liveness.txt) |
| 3 | `curl -i …/actuator/health/readiness` | — | 200 UP | 14:23:56 · **200** `{"status":"UP"}` | [`C01-health-readiness.txt`](respuestas/C01-health-readiness.txt) |

**Observaciones.** En `prod`, `show-details` y `show-components` están en `never`, así que la respuesta no detalla PostgreSQL ni Key Vault. Su integración se demuestra de forma indirecta: el perfil `prod` detiene el arranque si faltan `JWT_SECRET`, `DB_APP_PASSWORD` o `DB_OWNER_PASSWORD`, que el App Service toma de Key Vault como referencias `@Microsoft.KeyVault(...)` (`app_service.tf`); y las SIT de este mismo día ([CAL-136](../CAL-136-catalogo/README.md) a [CAL-144](../CAL-144-ventas-analitica/README.md)) iniciaron sesión con JWT firmados y leyeron y escribieron en PostgreSQL a través de esta misma API.

### C02 — Un restaurante no alcanza la orden ni la mesa de otro

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | — |
| Inicio / fin de ejecución | — |
| Estado | ⏭️ No ejecutado |
| Defecto vinculado | — |

**Motivo.** Se necesita un segundo restaurante con una orden y una mesa reales para pedirlas desde la sesión de `pruebas`. Solo se tiene acceso a `pruebas`, y dar de alta un restaurante nuevo en producción no estaba autorizado en esta ejecución. El procedimiento ya está preparado en [`../comun/restaurante-b.mjs`](../comun/restaurante-b.mjs) (alta pública, mesa, producto y venta) para ejecutarlo en cuanto se autorice o se facilite un restaurante de prueba existente. Pedir un UUID inventado devolvería 404 sin demostrar RLS, por eso no se usa como sustituto.

### C03 — Cabeceras de seguridad en el frontend y la API

| Campo | Valor |
|---|---|
| Criterio cubierto | 3 |
| Prioridad | Alta |
| Rol que ejecuta | Sin sesión |
| Inicio / fin de ejecución | `2026-10-28 14:23` / `2026-10-28 14:23` |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-183](https://linear.app/callejon19/issue/CAL-183) |

**Precondición.** Ninguna.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `curl -i https://restohub.azurewebsites.net/login` | — | CSP, HSTS, X-Frame-Options, nosniff | 14:23:56 · **200** con `content-security-policy` (nonce + `strict-dynamic`, `frame-ancestors 'none'`), `strict-transport-security: max-age=31536000`, `x-frame-options: DENY`, `x-content-type-options: nosniff` | [`C03-cabeceras-frontend.txt`](respuestas/C03-cabeceras-frontend.txt) |
| 2 | `curl -i …api…/actuator/health` | — | Las cuatro | 14:23:56 · **200** con CSP, `x-frame-options: DENY`, `nosniff`, `referrer-policy`. **Sin `strict-transport-security`** | [`C03-cabeceras-api-health.txt`](respuestas/C03-cabeceras-api-health.txt) |
| 3 | `curl -i …api…/api/v1/orders` (ruta protegida, sin sesión) | — | Las cuatro, con 401 | 14:23:57 · **401** con CSP, DENY y nosniff. **Sin HSTS** | [`C03-cabeceras-api-protegida.txt`](respuestas/C03-cabeceras-api-protegida.txt) |
| 4 | `curl -i http://…api…/actuator/health` (sin TLS) | — | Redirección a HTTPS | 14:23:57 · **301** a `https://…/actuator/health` | [`C03-api-por-http.txt`](respuestas/C03-api-por-http.txt) |
| 5 | `curl -i https://restohub…/api/v1/orders` (API por el proxy) | — | Las cuatro | 14:23:57 · **401** con CSP de la API, DENY y nosniff. **Sin HSTS** | [`C03-cabeceras-proxy-api.txt`](respuestas/C03-cabeceras-proxy-api.txt) |

**Observaciones.** El frontend cumple las cuatro cabeceras. La API cumple CSP, X-Frame-Options y nosniff, pero no envía HSTS en ninguna respuesta. El App Service redirige HTTP a HTTPS (paso 4), lo que reduce el riesgo, y el navegador recibe HSTS para `restohub.azurewebsites.net` desde las páginas del frontend; pero el host de la API, que usa la app móvil, queda sin HSTS. Causa probable: `SecurityConfig` solo emite HSTS si la petición le llega como segura, y con `server.forward-headers-strategy: none` detrás de la terminación TLS de App Service la ve como HTTP.

### C04 — Swagger UI y `/v3/api-docs` en producción

| Campo | Valor |
|---|---|
| Criterio cubierto | 4 |
| Prioridad | Alta |
| Rol que ejecuta | Sin sesión |
| Inicio / fin de ejecución | `2026-10-28 14:23` / `2026-10-28 14:23` |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-182](https://linear.app/callejon19/issue/CAL-182) |

**Precondición.** Según el criterio, `swagger_enabled = false` en el despliegue.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `curl -i …api…/swagger-ui.html` | — | 404 o 401 | 14:23:58 · **302** a `/swagger-ui/index.html` | [`C04-swagger-ui-html.txt`](respuestas/C04-swagger-ui-html.txt) |
| 2 | `curl -i …api…/swagger-ui/index.html` | — | 404 o 401 | 14:23:58 · **200** `text/html`, la página de Swagger UI; `last-modified: Wed, 28 Oct 2026 16:52:51 GMT`, la hora del despliegue de `fb36760` | [`C04-swagger-ui-index.txt`](respuestas/C04-swagger-ui-index.txt) |
| 3 | `curl -i …api…/v3/api-docs` | — | 404 o 401 | 14:23:58 · **200** `application/json`, 29 115 bytes: el contrato OpenAPI 3.1.0 completo, «Callejon 9 SaaS API», con 34 rutas `/api/v1/*` | [`C04-api-docs.txt`](respuestas/C04-api-docs.txt) |
| 4 | `curl -i https://restohub…/v3/api-docs` (por el frontend) | — | No expuesto | 14:23:58 · **404** del frontend: el proxy no reenvía esa ruta | [`C04-api-docs-proxy.txt`](respuestas/C04-api-docs-proxy.txt) |

**Observaciones.** El perfil `prod` apaga springdoc (`application-prod.yml`), y solo se enciende con `SPRINGDOC_APIDOCS_ENABLED` y `SPRINGDOC_SWAGGERUI_ENABLED`, que Terraform agrega cuando `swagger_enabled = true` (CAL-108, `infra/terraform/azure-backend/app_service.tf`). Que respondan indica que esas variables están activas en el App Service. El ejecutor no tiene acceso a la suscripción para confirmar si fue intencional. Si lo fue, la precondición del criterio no se cumple y el criterio debe reformularse; si no, es una exposición que debe apagarse.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-182](https://linear.app/callejon19/issue/CAL-182) | C04 | Alta | Swagger UI y el contrato OpenAPI completo responden 200 sin sesión en la API de producción | Abierto | — |
| [CAL-183](https://linear.app/callejon19/issue/CAL-183) | C03 | Media | La API desplegada no envía `Strict-Transport-Security` (sí CSP, X-Frame-Options y nosniff); HTTP sí redirige a HTTPS | Abierto | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 4 | 1 | 2 | 0 | 1 |

**Desviaciones y limitaciones.**

- **C02 no ejecutado.** Falta un segundo restaurante con datos; ver el motivo en el caso. El criterio de aislamiento queda sin evidencia sobre Azure en esta versión.
- **Key Vault y PostgreSQL de forma indirecta.** Producción oculta los componentes de health (sección 6, C01).
- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). La configuración de seguridad y de springdoc es la misma que en `main`.

**Conclusión del ejecutor.** La API desplegada está sana (C01). Fallan dos controles: la documentación interactiva y el contrato de la API están expuestos sin sesión en producción (C04, [CAL-182](https://linear.app/callejon19/issue/CAL-182)), y la API no envía HSTS (C03, [CAL-183](https://linear.app/callejon19/issue/CAL-183)). El aislamiento entre restaurantes (C02) sigue sin probarse sobre Azure. El issue no puede cerrarse en este estado.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C03, C04 | El proxy reenvía las cabeceras de la API y no expone `/v3/api-docs` |
| App móvil ↔ API | C03 | El host de la API, que usa la app, no envía HSTS |
| API ↔ PostgreSQL (RLS en la base real) | C01 (indirecto), C02 | C02 no ejecutado |
| API ↔ Key Vault | C01 (indirecto) | El perfil `prod` no arranca sin los secretos |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** `2026-10-28`
