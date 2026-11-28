# Reporte de Cumplimiento de Telemetría y SLA

> **Objetivo evaluado:** `Frontend-Produccion`  
> **Acuerdo de referencia:** `SLA-2026-RESTOHUB-001`  
> **Fecha del reporte:** 2026-11-27 15:39:49  
> **Ventana de medición:** Desde `2026-11-27T21:37:53.333194+00:00` hasta `2026-11-27T21:37:53.333194+00:00`  
> **Total de muestras (minutos):** 1

---

## 1. Resumen Ejecutivo de Cumplimiento

| Indicador (SLI) | Meta Contractual (SLO) | Valor Medido Real | Estado |
|---|---|---|---|
| **Disponibilidad Mensual** | $\ge 99.0\%$ | **100.00%** | **[CUMPLE]** |
| **Tiempo de Respuesta (p95)** | $p95 < 500\text{ ms}$ | **1015.21 ms** | **[ATENCION > 500ms]** |
| **Tasa de Errores HTTP 5xx** | $< 1.0\%$ | **0.00%** | **[CUMPLE]** |

---

## 2. Análisis Detallado de Telemetría

### A. Desglose de Latencia (Distribución de Percentiles)
* **Promedio:** `1015.21 ms`
* **Mediana (Percentil 50):** `1015.21 ms`
* **Percentil 90 (p90):** `1015.21 ms`
* **Percentil 95 (p95):** `1015.21 ms` *(Métrica clave del SLA)*
* **Percentil 99 (p99):** `1015.21 ms`

### B. Gestión del Presupuesto de Error (Error Budget)
* **Presupuesto mensual para SLA 99.0%:** `432 minutos`
* **Minutos de inactividad registrados:** `0.0 minutos`
* **Presupuesto de error restante (SLA 99.0%):** `432.0 minutos` (100.0% disponible)
* **Presupuesto de error restante (SLO 99.5%):** `216.0 minutos`

---

## 3. Registro de Eventos e Incidentes
* **Muestras exitosas (UP):** `1`
* **Muestras fallidas (DOWN):** `0`
* **Errores HTTP 5xx:** `0`

*Generado automáticamente por `scripts/generate-sla-report.py`.*
