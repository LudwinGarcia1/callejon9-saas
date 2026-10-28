# Evidencia de prueba · CAL-142 — SIT · Tickets

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Generación y descarga de ticket en PDF (`OpenPDF 2.4.0`), consulta de ticket por folio y rechazo con HTTP 404 ante folios inexistentes. Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-142](https://linear.app/callejon19/issue/CAL-142) — SIT · Tickets |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Tickets (`TicketController`, `TicketService`, generación de PDF) |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Registro de despliegue | Comentario «Despliegue» de Braulio Hernández en [CAL-118](https://linear.app/callejon19/issue/CAL-118), 2026-10-21 00:56 |
| Ejecución de CI | No aplica |
| Navegador / dispositivo | curl.exe 8.10.1 en Windows 11 (64-bit) |
| Restaurante de prueba | `sit-val-10281430` |
| Versión de esta evidencia | v2 — 2026-10-28 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Mercado Serrano Valeria | Asignado en Linear |
| Ejecutor de la prueba | García Gaytán Ludwin | Re-ejecución técnica con script automatizado `scripts/revalidar-sit-azure.ps1` |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia padre (CAL-118) | 2026-10-20 | 2026-10-28 | 2026-10-20 | | |
| Esta tarea (CAL-142) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Cobro de venta en C01 de CAL-141 |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Descarga de PDF y consulta de folios en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que el sistema genera y descarga comprobantes de venta en formato PDF (`application/pdf`) emitidos con OpenPDF en Spring Boot, y que la búsqueda de tickets por folio respeta el aislamiento retornando 404 para folios inexistentes o de otros restaurantes.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Descarga del ticket en PDF desde caja: tipo application/pdf con partidas y total | C01 |
| 2. Búsqueda por folio de otro restaurante o inexistente: 404 | C02 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Venta cobrada en Azure con ticket emitido (ID y folio obtenidos de CAL-141)
- [x] Sesión de Cajero activa con cookie `access_token`

## 6. Casos de prueba

### C01 — Descarga de comprobante PDF y consulta por folio

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /api/v1/tickets/{id}/pdf` con Cajero | 200 OK con Content-Type application/pdf | HTTP 200 OK, `Content-Type: application/pdf`, `Producer: OpenPDF 2.4.0` | [`C01-descarga-ticket-pdf-headers.txt`](respuestas/C01-descarga-ticket-pdf-headers.txt), archivo binario [`ticket.pdf`](ticket.pdf) |
| 2 | `GET /api/v1/tickets?folio=TCK-...` con Cajero | 200 OK con el ticket consultado | HTTP 200 OK, entidad con folio, subtotal $90.00, propina $9.00 y total $99.00 | [`C01-consulta-ticket-folio.txt`](respuestas/C01-consulta-ticket-folio.txt) |

### C02 — Búsqueda de ticket inexistente (404 Not Found)

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /api/v1/tickets?folio=TCK-INEXISTENTE-9999` | 404 Not Found | HTTP 404 Not Found, ProblemDetail `El ticket con folio TCK-INEXISTENTE-9999 no existe.` | [`C02-ticket-inexistente-404.txt`](respuestas/C02-ticket-inexistente-404.txt) |

## 7. Defectos encontrados

Ninguno. La generación de PDF con OpenPDF en `TicketService.java` opera correctamente en Azure App Service Linux.

## 8. Resumen de ejecución y desviaciones

Se obtuvo el archivo PDF real del ticket y las respuestas HTTP literales. La ruta de búsqueda por folio implementada en `TicketController.java:33` (`@GetMapping(params = "folio")`) devuelve 404 con RFC 7807 cuando el folio no existe en el restaurante.

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
