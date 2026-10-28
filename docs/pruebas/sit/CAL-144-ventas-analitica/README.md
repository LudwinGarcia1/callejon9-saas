# Evidencia de prueba — CAL-144 — SIT — Ventas, historial y analítica

> **v1.** C01 y C02 ejecutados y verificados contra el entorno de producción en Azure.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-144](https://linear.app/callejon19/issue/CAL-144) — SIT — Ventas, historial y analítica |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118/sit-probar-la-integracion-de-sistemas-en-azure-antes-de-la-uat) — SIT — Probar la integración de sistemas en Azure antes de la UAT |
| Tipo de prueba | SIT (System Integration Testing) |
| Módulo | Historial de ventas y panel de analítica ejecutiva |
| Entorno | Microsoft Azure (Producción) |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado (API) | `ea9b5c1` (`ea9b5c128e147957e56668a18e254061797de33e`) |
| Commit del frontend | `1efb78c` (RestoHub standalone PWA) |
| Dispositivos de prueba | PC Administrador / Panel Web (Google Chrome 155, 64 bits) |
| Restaurante de prueba | `pruebas` |
| Versión de esta evidencia | v1 — 2026-10-26 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Mercado Serrano Valeria | Asignada en Linear |
| Ejecutora de la prueba | Mercado Serrano Valeria | Verificación en vistas de ventas y analítica ejecutiva |
| Revisor | Octavio Duarte | **Distinto del ejecutor.** Sección 7 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Tarea (CAL-144) | 2026-10-21 | 2026-10-26 | 2026-10-26 22:50 | 2026-10-26 23:05 | Ejecución y consolidación de respuestas |

## 4. Alcance y Criterios Cubiertos

**Objetivo:** Verificar la integridad contable y la concordancia temporal entre las ventas liquidadas en caja y los informes agregados del negocio:
1. Historial de ventas del día (`GET /api/v1/sales?from=2026-10-26&to=2026-10-26`) refleja exactamente las órdenes cobradas, con el total, propina y métodos de pago correspondientes.
2. Panel de analítica del administrador (`GET /api/v1/analytics?from=2026-10-26&to=2026-10-26`) consolida los totales, corte de día en zona horaria local (`America/Mexico_City`) y cálculo correcto del ticket promedio y mezcla de métodos de pago.

---

## 5. Casos de Prueba Ejecutados

### C01 — Coincidencia de Ventas en Historial de Caja
* **Propósito:** Validar que la venta liquidada en `CAL-141` aparece en el historial sin discrepancias en centavos.
* **Pasos:**
  1. El cajero o administrador consulta las ventas del día mediante `GET /api/v1/sales?from=2026-10-26&to=2026-10-26`.
* **Resultado observado:**
  * La respuesta `200 OK` lista la venta generada por `$385.00` (subtotal `$350.00`, propina `$35.00`).
  * Los registros de pago coinciden con tarjeta `$200.00` y efectivo `$185.00`.
* **Evidencia:** `respuestas/C01-historial-ventas.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

### C02 — Panel de Analítica Ejecutiva y Consolidación Diaria
* **Propósito:** Demostrar que el servicio de analítica agrega las métricas del periodo agrupando ventas totales, ticket promedio y mezcla de pagos.
* **Pasos:**
  1. El administrador consulta la analítica del restaurante: `GET /api/v1/analytics?from=2026-10-26&to=2026-10-26`.
* **Resultado observado:**
  * Total facturado en el periodo concuerda con las ventas liquidadas.
  * Agrupación por método de pago (`paymentMix`) cuadra exactamente con las transacciones registradas.
  * El cálculo respeta la zona horaria de operación (`America/Mexico_City`).
* **Evidencia:** `respuestas/C02-analitica-ejecutiva.txt`.
* **Veredicto:** ✅ **Aprobado**.

---

## 6. Resumen de Resultados

| Caso | Descripción | Resultado | Defectos |
|---|---|:---:|---|
| **C01** | Historial de ventas refleja tickets liquidados con precisión exacta (200 OK) | ✅ **Aprobado** | Ninguno |
| **C02** | Analítica ejecutiva consolida facturación y mezcla de pagos sin divergencias (200 OK) | ✅ **Aprobado** | Ninguno |

---

## 7. Revisión Independiente

- [x] Cada criterio del issue tiene al menos un caso con resultado
- [x] Los endpoints probados coinciden con `SalesController` y `AnalyticsController` (`/api/v1/sales` y `/api/v1/analytics`)
- [x] El commit probado corresponde al registro de despliegue en Azure
- [x] No hay credenciales expuestas

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Octavio Duarte | 2026-10-26 | Aprobado | Cuadre al centavo de historial de ventas y panel de analítica verificado. |

## 8. Declaración de Autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y fecha indicados.

**Ejecutora:** Mercado Serrano Valeria — **Fecha:** 2026-10-26
