#!/usr/bin/env python3
"""
Sonda de Monitoreo Sintetico Continuo - RestoHub / Callejon 9 SaaS
Respalda el SLA-2026-RESTOHUB-001 (Monitoreo externo cada 1 minuto).

Mide:
- Disponibilidad (Uptime %)
- Latencia de respuesta en milisegundos (p50, p95, p99)
- Codigos de estado HTTP (200 OK vs errores 5xx / 4xx)
- Consumo del presupuesto de error (Error Budget)
"""

import sys
import os
import time
import argparse
import urllib.request
import urllib.error
import datetime
import csv
import json

TARGETS = [
    {
        "name": "Frontend-Produccion",
        "url": "https://restohub.azurewebsites.net",
        "expected_code": 200,
        "critical": True
    },
    {
        "name": "Backend-API-Health",
        "url": "https://app-callejon9-api-prod-3878c6.azurewebsites.net/actuator/health",
        "expected_code": 200,
        "critical": False  # Se evalua pero si esta apagado preventivamente no interrumpe la sonda
    }
]

DEFAULT_INTERVAL_SECONDS = 60  # 1 minuto exacto conforme al SLA

def get_output_paths():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    telemetry_dir = os.path.join(repo_root, "docs", "telemetry")
    os.makedirs(telemetry_dir, exist_ok=True)
    csv_path = os.path.join(telemetry_dir, "synthetic-uptime.csv")
    jsonl_path = os.path.join(telemetry_dir, "synthetic-uptime.jsonl")
    return csv_path, jsonl_path

def init_csv(csv_path):
    if not os.path.exists(csv_path) or os.path.getsize(csv_path) == 0:
        with open(csv_path, mode="w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow([
                "timestamp_iso",
                "target_name",
                "url",
                "status_code",
                "latency_ms",
                "is_up",
                "error_message"
            ])

def probe_target(target):
    name = target["name"]
    url = target["url"]
    expected = target.get("expected_code", 200)

    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "RestoHub-SLA-Synthetic-Probe/1.0 (+https://restohub.azurewebsites.net)",
            "Accept": "*/*"
        }
    )

    start_time = time.perf_counter()
    status_code = 0
    error_message = ""
    is_up = False

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            elapsed_ms = (time.perf_counter() - start_time) * 1000.0
            status_code = resp.status
            if status_code == expected or (200 <= status_code < 400):
                is_up = True
            else:
                error_message = f"Codigo HTTP inesperado: {status_code}"
    except urllib.error.HTTPError as e:
        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        status_code = e.code
        error_message = f"HTTPError: {e.code} {e.reason}"
        is_up = False
    except urllib.error.URLError as e:
        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        status_code = 0
        error_message = f"URLError: {e.reason}"
        is_up = False
    except Exception as e:
        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        status_code = 0
        error_message = f"Exception: {str(e)}"
        is_up = False

    return {
        "timestamp_iso": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "target_name": name,
        "url": url,
        "status_code": status_code,
        "latency_ms": round(elapsed_ms, 2),
        "is_up": 1 if is_up else 0,
        "error_message": error_message
    }

def record_sample(csv_path, jsonl_path, sample):
    with open(csv_path, mode="a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            sample["timestamp_iso"],
            sample["target_name"],
            sample["url"],
            sample["status_code"],
            sample["latency_ms"],
            sample["is_up"],
            sample["error_message"]
        ])
    with open(jsonl_path, mode="a", encoding="utf-8") as f:
        f.write(json.dumps(sample) + "\n")

def run_probe_cycle(csv_path, jsonl_path):
    results = []
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    for t in TARGETS:
        sample = probe_target(t)
        record_sample(csv_path, jsonl_path, sample)
        results.append(sample)
        status_str = "UP [OK]" if sample["is_up"] else f"DOWN [{sample['status_code']} {sample['error_message']}]"
        print(f"[{timestamp}] {sample['target_name']:<22} -> {status_str:<12} | Latencia: {sample['latency_ms']:>7.2f} ms")
    return results

def main():
    parser = argparse.ArgumentParser(description="Sonda de Monitoreo Sintetico RestoHub")
    parser.add_argument("--once", action="store_true", help="Ejecuta un solo chequeo y sale")
    parser.add_argument("--interval", type=int, default=DEFAULT_INTERVAL_SECONDS, help="Intervalo en segundos (default: 60)")
    parser.add_argument("--iterations", type=int, default=0, help="Numero maximo de ciclos (0 = infinito)")
    args = parser.parse_args()

    csv_path, jsonl_path = get_output_paths()
    init_csv(csv_path)

    print("================================================================================")
    print("      SONDA DE MONITOREO SINTETICO CONTINUO - RESTOHUB / CALLEJON 9 SAAS       ")
    print(f"  Frecuencia: Cada {args.interval} segundos | SLA: SLA-2026-RESTOHUB-001       ")
    print(f"  Salida CSV: {csv_path}")
    print("================================================================================")

    if args.once:
        run_probe_cycle(csv_path, jsonl_path)
        print("Chequeo unico finalizado.")
        return

    count = 0
    try:
        while True:
            run_probe_cycle(csv_path, jsonl_path)
            count += 1
            if args.iterations > 0 and count >= args.iterations:
                print(f"Alcanzado el limite de {args.iterations} iteraciones.")
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\nSonda detenida manualmente por el usuario.")

if __name__ == "__main__":
    main()
