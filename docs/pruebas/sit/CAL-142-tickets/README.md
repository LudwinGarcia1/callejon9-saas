# Evidencia de prueba — CAL-142 — SIT — Tickets

> **v1.** C01 y C02 ejecutados y verificados contra el entorno de producción en Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-142](https://linear.app/callejon19/issue/CAL-142) — SIT — Tickets |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT — Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Emisión, consulta y descarga PDF de tickets |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | Terminal de cobro (Google Chrome 155, 64 bits) |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 — 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Mercado Serrano Valeria | Asignada en Linear |
| Ejecutora de la prueba | Mercado Serrano Valeria | Verificación en endpoints de tickets y visor PDF |
| Revisor | Octavio Duarte | **Distinto del ejecutor.** Sección 7 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-142) | 2026-10-21 | 2026-10-26 | 2026-10-26 22:45 | 2026-10-26 23:00 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Verificar la consulta de comprobantes inmutables y la generación binaria PDF (`application/pdf`) en el entorno cloud de producción:
1. Consulta de ticket por folio (`GET /api/v1/tickets?folio=TCK-2026-0091`) y descarga binaria de PDF (`GET /api/v1/tickets/{id}/pdf`) renderizable para impresión térmica de 80mm.
2. Aislamiento estricto multi-inquilino (RLS): intento de consulta de folio o UUID perteneciente a otro restaurante retorna `HTTP 404 Not Found` sin filtrar información.

---

## 5. Casos de Prueba Ejecutados

### C01 — Consulta por Folio y Descarga de Comprobante PDF
* **Propósito:** Validar que el ticket emitido se localiza por su folio y se genera el archivo binario PDF con cabecera `Content-Type: application/pdf`.
* **Pasos:**
  1. El cajero busca el ticket emitido en el cobro mediante `GET /api/v1/tickets?folio=TCK-2026-0091`.
  2. La API responde `200 OK` con los datos del comprobante.
  3. Se solicita el binario para impresión mediante `GET /api/v1/tickets/t1a80123-0001-4000-8000-000000000091/pdf`.
* **Resultado observado:**
  * La consulta por folio devuelve el ticket con sus partidas congeladas, importes e información de caja.
  * La descarga PDF entrega un flujo binario `application/pdf` con la firma estándar `%PDF-1.4`.
* **Evidencia:** `respuestas/C01-ticket-por-folio.txt` y `respuestas/C01-descarga-pdf.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Aislamiento RLS en Tickets de Otro Inquilino
* **Propósito:** Asegurar que ningún usuario pueda consultar folios emitidos en otro restaurante.
* **Pasos:**
  1. Un usuario autenticado en el restaurante `pruebas` consulta un folio de otro inquilino: `GET /api/v1/tickets?folio=TCK-2026-9999`.
* **Resultado observado:**
  * La cláusula de RLS filtra el ticket en PostgreSQL (`tenant_id = current_setting('app.tenant_id')`).
  * La API responde `HTTP/1.1 404 Not Found`.
  * Detalle de error: `"El ticket con folio TCK-2026-9999 no existe."`.
* **Evidencia:** `respuestas/C02-rechazo-ticket-ajeno-404.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Búsqueda por folio y generación de PDF binario (200 OK) | ✅ **Aprobado** | Ninguno |
| **C02** | Aislamiento entre inquilinos garantizado por RLS (404 Not Found) | ✅ **Aprobado** | Ninguno |

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Endpoints probados coinciden con `TicketController` (`GET /api/v1/tickets?folio=` y `GET /api/v1/tickets/{id}/pdf`)
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay credenciales expuestas

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Generación de PDF y protección de folios confirmados. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutora:** Mercado Serrano Valeria — **Fecha:** 2026-10-26
