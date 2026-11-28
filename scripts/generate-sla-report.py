#!/usr/bin/env python3
"""
Generador de Reporte y Metricas de SLA a partir de Telemetria Sintetica
Calcula:
- Porcentaje de Disponibilidad (Uptime %)
- Percentiles de Latencia (p50, p95, p99)
- Balance y Burn Rate del Error Budget (Presupuesto de Error)
- Comparativa contra SLO (99.5%) y SLA contractual (99.0%)
"""

import os
import csv
import math
import argparse
import datetime

def calculate_percentile(data, p):
    if not data:
        return 0.0
    sorted_data = sorted(data)
    k = (len(sorted_data) - 1) * (p / 100.0)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_data[int(k)]
    d0 = sorted_data[int(f)] * (c - k)
    d1 = sorted_data[int(c)] * (k - f)
    return d0 + d1

def generate_report(target_filter="Frontend-Produccion"):
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    csv_path = os.path.join(repo_root, "docs", "telemetry", "synthetic-uptime.csv")
    report_md_path = os.path.join(repo_root, "docs", "telemetry", "SLA-Report-Current.md")

    if not os.path.exists(csv_path):
        print(f"No se encontro el archivo de telemetria en {csv_path}")
        return

    rows = []
    with open(csv_path, mode="r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            if target_filter and r["target_name"] != target_filter:
                continue
            rows.append(r)

    if not rows:
        print(f"No hay muestras registradas para el objetivo '{target_filter}'.")
        return

    total_samples = len(rows)
    up_samples = sum(1 for r in rows if r["is_up"] == "1")
    down_samples = total_samples - up_samples
    availability_pct = (up_samples / total_samples) * 100.0

    latencies = [float(r["latency_ms"]) for r in rows if r["latency_ms"]]
    p50 = calculate_percentile(latencies, 50)
    p90 = calculate_percentile(latencies, 90)
    p95 = calculate_percentile(latencies, 95)
    p99 = calculate_percentile(latencies, 99)
    avg_latency = sum(latencies) / len(latencies) if latencies else 0.0

    errors_5xx = sum(1 for r in rows if r["status_code"].startswith("5"))
    error_rate_pct = (errors_5xx / total_samples) * 100.0

    # Error Budget (para 30 dias = 43200 minutos)
    # SLA 99.0% -> 432 min de margen
    # SLO 99.5% -> 216 min de margen
    total_budget_sla_min = 432.0
    total_budget_slo_min = 216.0
    # Cada muestra representa 1 minuto de operacion
    downtime_minutes_estimated = down_samples * 1.0
    remaining_budget_sla = max(0.0, total_budget_sla_min - downtime_minutes_estimated)
    remaining_budget_slo = max(0.0, total_budget_slo_min - downtime_minutes_estimated)

    first_sample = rows[0]["timestamp_iso"]
    last_sample = rows[-1]["timestamp_iso"]

    status_sla = "[CUMPLE]" if availability_pct >= 99.0 else "[INCUMPLE]"
    status_p95 = "[CUMPLE]" if p95 < 500.0 else "[ATENCION > 500ms]"
    status_5xx = "[CUMPLE]" if error_rate_pct < 1.0 else "[INCUMPLE]"

    report_text = f"""# Reporte de Cumplimiento de Telemetría y SLA

> **Objetivo evaluado:** `{target_filter}`  
> **Acuerdo de referencia:** `SLA-2026-RESTOHUB-001`  
> **Fecha del reporte:** {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  
> **Ventana de medición:** Desde `{first_sample}` hasta `{last_sample}`  
> **Total de muestras (minutos):** {total_samples}

---

## 1. Resumen Ejecutivo de Cumplimiento

| Indicador (SLI) | Meta Contractual (SLO) | Valor Medido Real | Estado |
|---|---|---|---|
| **Disponibilidad Mensual** | $\\ge 99.0\\%$ | **{availability_pct:.2f}%** | **{status_sla}** |
| **Tiempo de Respuesta (p95)** | $p95 < 500\\text{{ ms}}$ | **{p95:.2f} ms** | **{status_p95}** |
| **Tasa de Errores HTTP 5xx** | $< 1.0\\%$ | **{error_rate_pct:.2f}%** | **{status_5xx}** |

---

## 2. Análisis Detallado de Telemetría

### A. Desglose de Latencia (Distribución de Percentiles)
* **Promedio:** `{avg_latency:.2f} ms`
* **Mediana (Percentil 50):** `{p50:.2f} ms`
* **Percentil 90 (p90):** `{p90:.2f} ms`
* **Percentil 95 (p95):** `{p95:.2f} ms` *(Métrica clave del SLA)*
* **Percentil 99 (p99):** `{p99:.2f} ms`

### B. Gestión del Presupuesto de Error (Error Budget)
* **Presupuesto mensual para SLA 99.0%:** `432 minutos`
* **Minutos de inactividad registrados:** `{downtime_minutes_estimated:.1f} minutos`
* **Presupuesto de error restante (SLA 99.0%):** `{remaining_budget_sla:.1f} minutos` ({((remaining_budget_sla/total_budget_sla_min)*100):.1f}% disponible)
* **Presupuesto de error restante (SLO 99.5%):** `{remaining_budget_slo:.1f} minutos`

---

## 3. Registro de Eventos e Incidentes
* **Muestras exitosas (UP):** `{up_samples}`
* **Muestras fallidas (DOWN):** `{down_samples}`
* **Errores HTTP 5xx:** `{errors_5xx}`

*Generado automáticamente por `scripts/generate-sla-report.py`.*
"""

    with open(report_md_path, mode="w", encoding="utf-8") as f:
        f.write(report_text)

    print("================================================================================")
    print("                REPORTE DE CUMPLIMIENTO SLA - RESTOHUB                         ")
    print("================================================================================")
    print(f" Objetivo:                {target_filter}")
    print(f" Muestras analizadas:     {total_samples} minutos")
    print(f" Disponibilidad medida:   {availability_pct:.2f}% {status_sla}")
    print(f" Latencia p50 (Mediana):  {p50:.2f} ms")
    print(f" Latencia p95:            {p95:.2f} ms {status_p95}")
    print(f" Latencia p99:            {p99:.2f} ms")
    print(f" Tasa de errores 5xx:     {error_rate_pct:.2f}% {status_5xx}")
    print(f" Error Budget restante:   {remaining_budget_sla:.1f} min de 432 min")
    print(f" Archivo Markdown:        {report_md_path}")
    print("================================================================================")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Calculador de SLA")
    parser.add_argument("--target", default="Frontend-Produccion", help="Objetivo a filtrar")
    args = parser.parse_args()
    generate_report(args.target)
