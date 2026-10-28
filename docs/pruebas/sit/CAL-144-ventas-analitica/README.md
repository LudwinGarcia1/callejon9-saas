# Evidencia de prueba · CAL-144 — SIT · Ventas, historial y analítica

> **v2.** Re-ejecución real contra Azure (2026-10-28) con respuestas HTTP literales de `curl.exe -i`. Conciliación de ventas registradas en caja contra el historial (`GET /api/v1/sales`) y agregados analíticos ejecutivos (`GET /api/v1/analytics`). Pendiente de revisión independiente (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-144](https://linear.app/callejon19/issue/CAL-144) — SIT · Ventas, historial y analítica |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT |
| Módulo | Ventas, Historial y Analítica (`SalesController`, `AnalyticsController`, `SaleHistoryService`, `AnalyticsService`) |
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
| Esta tarea (CAL-144) | 2026-10-21 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:45 | Re-ejecución tras observaciones de revisión independiente |
| Preparación del entorno | 2026-10-28 | 2026-10-28 | 2026-10-28 14:30 | 2026-10-28 14:33 | Cobros reales efectuados en CAL-141 |
| Ejecución de casos | 2026-10-28 | 2026-10-28 | 2026-10-28 14:44 | 2026-10-28 14:45 | Captura de respuestas literales en Azure |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar que las ventas cobradas en caja se consolidan íntegramente en el historial de ventas (`GET /api/v1/sales`) con desglose por forma de pago, y que el panel de analítica (`GET /api/v1/analytics`) refleja los mismos totales, análisis de Pareto y ventas diarias de acuerdo con el calendario operativo.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| 1. Las ventas del día en caja coinciden con el historial de ventas | C01 |
| 2. La analítica del administrador refleja esos mismos totales en el periodo | C02 |

## 5. Preparación

**Precondiciones del entorno**
- [x] Backend encendido y `/actuator/health` en `UP`
- [x] Cobros ejecutados en Azure con ticket emitido (2 ventas de prueba por $99.00 cada una, subtotal $90.00 + propina $9.00)
- [x] Sesión de Administrador activa con cookie `access_token`

## 6. Casos de prueba

### C01 — Historial consolidado de ventas del día

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /api/v1/sales` con Administrador | 200 OK con desglose de ventas, total $198.00 y mezcla de pago | HTTP 200 OK, `count: 2, total: 198.00`, CASH $100.00 + CARD $98.00 | [`C01-historial-ventas.txt`](respuestas/C01-historial-ventas.txt) |

### C02 — Analítica ejecutiva: Pareto, ventas diarias y mezcla de métodos

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | `GET /api/v1/analytics` con Administrador | 200 OK con Pareto, ventas por día y mezcla de pago | HTTP 200 OK, Pareto con productos vendidos ($90 + $90), venta del día 2026-10-28 en $198.00, CASH 50.51% y CARD 49.49% | [`C02-analitica-ejecutiva.txt`](respuestas/C02-analitica-ejecutiva.txt) |

## 7. Defectos encontrados

Ninguno. El cuadre aritmético entre historial (`SalesController`) y analítica (`AnalyticsController`) es idéntico a nivel de centavos.

## 8. Resumen de ejecución y desviaciones

La consulta a los endpoints de ventas en Azure confirmó que las transacciones liquidadas en caja alimentan de manera inmediata tanto el kardex contable de auditoría como las métricas analíticas gerenciales.

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
