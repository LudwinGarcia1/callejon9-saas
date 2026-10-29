# Evidencia de prueba · CAL-142 — SIT · Tickets

> **v2 · re-ejecución.** La v1 (2026-10-26) se retiró: sus respuestas HTTP no provenían del backend desplegado (IDs que no son UUID) y la venta que citaba no existía. Ver el comentario del 27-oct en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat). Esta versión se ejecutó de nuevo sobre Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-142](https://linear.app/callejon19/issue/CAL-142/sit-tickets) — SIT · Tickets |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Tickets (web: `/history`; backend `ticket`) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` (la web la consume por el proxy `/api/v1/*` de Next.js) |
| Commit probado (API) | `fb36760`, rama `fix/cal-169-hotfix-azure` (base `ea9b5c1` + corrección de `ClientIp`; sin migraciones nuevas) |
| Registro de despliegue | API: comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat), 2026-10-28 10:56. Frontend: comentario «Despliegue» de Octavio Duarte en CAL-118, 2026-10-22 |
| Commit del frontend | `e6a6782` (`main`) |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | Brave 156.1.98.60 (Chromium 156.0.8078.48) automatizado con Playwright 1.56.1 en Garuda Linux, ventana de 1440 × 900; texto del PDF extraído con `pdftotext` |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v2 · 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear (reasignado el 2026-10-28) |
| Ejecutor de la prueba | Octavio Duarte | Script de Playwright de esta carpeta |
| Revisor | Braulio Hernández Mejía | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-118) | 2026-10-20 | 2026-10-21 | 2026-10-20 | | Sigue abierta: 7 SIT se re-ejecutan |
| Esta tarea (CAL-142) | 2026-10-21 | 2026-10-21 | 2026-10-21 | | v1 retirada el 2026-10-27; re-ejecución el 2026-10-28 |
| Preparación del entorno y datos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:11 | 2026-10-28 14:34 | La venta de C01 es la de la SIT de CAL-141; el restaurante B se dio de alta a las 14:34, una vez autorizado |
| Ejecución de los casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:16 | 2026-10-28 14:34 | C01 a las 14:16; C02 a las 14:34, tras crear el restaurante B |
| Re-prueba de defectos | | | | | Sin defectos |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el ticket de una venta cobrada en Azure se recupera por folio desde caja y se descarga como PDF con sus datos, y que un folio de otro restaurante no se puede consultar.

**Fuera de alcance.** La impresión térmica de 80 mm y la app móvil (no emite tickets).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Descarga del ticket en PDF desde caja: tipo `application/pdf`, con folio, partidas, propina y total de la venta cobrada. | C01 |
| 2. Búsqueda por folio de otro restaurante: 404. | C02 |

## 5. Preparación

**Precondiciones del entorno**

- [x] Backend encendido y `/actuator/health` en `UP` — [`respuestas/health.txt`](respuestas/health.txt), 2026-10-28 14:16
- [x] Commit desplegado tomado del registro de despliegue: API `fb36760`, frontend `e6a6782`
- [x] Frontend accesible en `https://restohub.azurewebsites.net`
- [x] Venta cobrada: la de [CAL-141](../CAL-141-cobro-pagos/README.md) C01, ticket `TCK-261028201211` — 2 × Guacamole con totopos y 1 × Margarita, subtotal $325.00, propina 10 % $32.50, total $357.50, efectivo $200.00 y tarjeta $157.50
- [x] Segundo restaurante para C02, dado de alta por el registro público con autorización del responsable (2026-10-28 14:34), igual que en la SIT de CAL-134: «SIT Restaurante B», slug `sit-restaurante-b-28101434`, plan FREE. Su administrador creó la mesa 1, un producto de $50.00 y cobró la orden `ORD-261028203443` → ticket `TCK-261028203444` — [`PREP-B-01`](respuestas/PREP-B-01-alta-restaurante-b.txt) a [`PREP-B-06`](respuestas/PREP-B-06-cobro.txt), [`estado-restaurante-b.json`](respuestas/estado-restaurante-b.json)

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Caja | `caja@pruebas.com` (CASHIER) | Alta de usuarios de prueba del 2026-10-16 |
| Ticket | `TCK-261028201211`, id `564335dd-e8a5-4e8a-ad0f-5456d205308f` | Cobro de CAL-141 C01 |
| Administrador del restaurante B | `admin@sit-restaurante-b-28101434.test` (ADMIN) | Alta pública del restaurante B; su contraseña se generó al azar y vive fuera del repositorio (`~/.config/callejon9-sit-restaurante-b-password`) |
| Ticket del restaurante B | `TCK-261028203444`, id `4468b4d9-ec20-4e5c-b545-5fa5f42760d0` | Cobro del administrador de B en la preparación |
| Contraseña | La de las cuentas de prueba | Vive fuera del repositorio; el script la lee de `C9_TEST_PASSWORD` |

## 6. Casos de prueba

Los pasos los ejecutó [`playwright/sit-tickets.mjs`](playwright/sit-tickets.mjs): `casos` para C01, `preparacion` para el restaurante B y `c02` para C02. Las peticiones de la interfaz están en [`peticiones-de-la-interfaz.txt`](respuestas/peticiones-de-la-interfaz.txt). Cada captura lleva una banda roja con la URL y la hora.

### C01 — Caja descarga el ticket en PDF con folio, partidas, propina y total

| Campo | Valor |
|---|---|
| Criterio cubierto | 1 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER (`caja@pruebas.com`) |
| Inicio / fin de ejecución | `2026-10-28 14:16` / `2026-10-28 14:16` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Sesión de caja; la venta de CAL-141 cobrada.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Caja: «Historial» → «Buscar ticket por folio» | `TCK-261028201211` | Se muestra el ticket | 14:16:38 · `GET /api/v1/tickets?folio=TCK-261028201211` → **200**; resumen del ticket con «Descargar ticket en PDF» | [`C01-P01`](capturas/C01-P01-ticket-encontrado.png), [`C01-ticket-por-folio.txt`](respuestas/C01-ticket-por-folio.txt) |
| 2 | Tocar «Descargar ticket en PDF» | — | `application/pdf` | 14:16:40 · `GET /api/v1/tickets/564335dd-…/pdf` → **200**, `Content-Type: application/pdf`, 1 334 bytes; el navegador lo guarda como `ticket-TCK-261028201211.pdf` | [`C01-P02`](capturas/C01-P02-pdf-descargado.png), [`C01-descarga-pdf.txt`](respuestas/C01-descarga-pdf.txt), [`C01-ticket-TCK-261028201211.pdf`](respuestas/C01-ticket-TCK-261028201211.pdf) |
| 3 | Extraer el texto del PDF | `pdftotext -layout` | Folio, partidas, propina y total | Folio `TCK-261028201211`; «Guacamole con totopos 2 · 95.00 · 190.00»; «Margarita 1 · 135.00 · 135.00»; «Subtotal: $325.00»; «Propina: $32.50»; «Total: $357.50»; «Metodo de pago: Mixto», Efectivo $200.00 y Tarjeta $157.50 | [`C01-texto-del-pdf.txt`](respuestas/C01-texto-del-pdf.txt) |

**Observaciones.** El PDF imprime la fecha en UTC («28/10/2026 20:12 UTC»), no en la hora local del restaurante (14:12). No es un defecto del criterio; se deja como posible mejora.

### C02 — Un folio de otro restaurante no se encuentra

| Campo | Valor |
|---|---|
| Criterio cubierto | 2 |
| Prioridad | Alta |
| Rol que ejecuta | CASHIER de `pruebas` (`caja@pruebas.com`); control con el ADMIN del restaurante B |
| Inicio / fin de ejecución | `2026-10-28 14:34` / `2026-10-28 14:34` |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** El restaurante B tiene una venta cobrada con ticket `TCK-261028203444`; la caja de `pruebas` tiene sesión.

**Pasos para reproducir**

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Caja de `pruebas`: «Historial» → «Buscar ticket por folio» | `TCK-261028203444` (del restaurante B) | 404 y aviso | 14:34:50 · `GET /api/v1/tickets?folio=TCK-261028203444` → **404**; aviso «No existe ningún ticket con ese folio.»; el historial solo muestra la venta propia | [`C02-P01`](capturas/C02-P01-folio-de-otro-restaurante.png), [`C02-folio-de-otro-restaurante.txt`](respuestas/C02-folio-de-otro-restaurante.txt) |
| 2 | Pedir ese ticket por su id | `GET /api/v1/tickets/4468b4d9-…` | 404 | 14:34:50 · **404** | [`C02-ticket-de-otro-restaurante-por-id.txt`](respuestas/C02-ticket-de-otro-restaurante-por-id.txt) |
| 3 | Pedir su PDF | `GET /api/v1/tickets/4468b4d9-…/pdf` | 404 | 14:34:51 · **404** | [`C02-pdf-de-otro-restaurante.txt`](respuestas/C02-pdf-de-otro-restaurante.txt) |
| 4 | Control: el administrador de B busca el mismo folio en su historial | `TCK-261028203444` | Lo encuentra | 14:34:56 · resumen del ticket; `GET …?folio=` → **200** | [`C02-P02`](capturas/C02-P02-restaurante-b-encuentra-su-folio.png), [`C02-restaurante-b-su-propio-folio.txt`](respuestas/C02-restaurante-b-su-propio-folio.txt) |

**Respuesta HTTP literal**

```text
# Caja de `pruebas` pide el ticket del restaurante B por folio
GET /api/v1/tickets?folio=TCK-261028203444

HTTP 404
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "El ticket con folio TCK-261028203444 no existe.",
  "instance": "/api/v1/tickets"
}
```

**Observaciones.** El paso 4 demuestra que el folio existe: el 404 de los pasos 1 a 3 viene del aislamiento entre restaurantes, no de un folio inválido. El restaurante B queda en producción con su venta de $50.00; es de prueba y lo reutiliza CAL-145 C02.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Sin defectos | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- **Restaurante B.** Para C02 se dio de alta un restaurante de prueba en producción, con autorización del responsable.
- **Commit de la API fuera de `main`.** `fb36760` es un hotfix desplegado sin fusionar (PR #105). No toca tickets.

**Conclusión del ejecutor.** El criterio 1 se cumple sobre Azure: caja recupera por folio el ticket de la venta de la SIT y descarga un PDF real con folio, partidas, propina y total. El criterio 2 también: el folio, el id y el PDF de un ticket de otro restaurante responden 404, mientras su dueño sí lo encuentra.

## 9. Anexos por tipo de prueba

### SIT — integración de sistemas

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Frontend ↔ API (proxy `/api/v1/*`) | C01 | `GET /tickets?folio=` 200 y `GET /tickets/{id}/pdf` 200 `application/pdf` desde el navegador |
| App móvil ↔ API | — | No aplica |
| API ↔ PostgreSQL (RLS en la base real) | C01, C02 | C01 lee la venta escrita en CAL-141; C02: el ticket de B es invisible para `pruebas` y visible para B |
| API ↔ tiempo real (`/ws`) | — | No aplica |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las capturas muestran la URL y la hora (banda de la captura automatizada) y coinciden con los pasos
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado corresponde al registro de despliegue
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las capturas y respuestas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** `2026-10-28`
