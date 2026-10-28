#!/usr/bin/env bash
# SIT de CAL-145, casos que se verifican con HTTP directo (sin sesión):
#   C01  /actuator/health, liveness y readiness de la API desplegada
#   C03  cabeceras de seguridad del frontend y de la API
#   C04  Swagger UI y /v3/api-docs apagados en producción
# Cada respuesta se guarda literal (curl -i) en ../respuestas/. Ninguna petición
# lleva credenciales. Uso: bash sit-seguridad.sh
set -euo pipefail

API="${API_URL:-https://app-callejon9-api-prod-3878c6.azurewebsites.net}"
WEB="${BASE_URL:-https://restohub.azurewebsites.net}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/respuestas"
mkdir -p "$OUT"

now() { TZ=America/Mexico_City date '+%Y-%m-%d %H:%M:%S'; }

# save <archivo> <título> <url> [método]
save() {
  local file="$1" title="$2" url="$3" method="${4:-GET}"
  {
    echo "# $title"
    echo "# $(now) America/Mexico_City · directo, sin sesión"
    echo "\$ curl -sS -i -X $method $url"
    curl -sS -i -X "$method" --max-time 30 "$url" | sed -E 's/^(set-cookie:).*/\1 <redactado>/I'
    echo
  } > "$OUT/$file"
  printf '%s  %-45s %s\n' "$(now)" "$file" "$(sed -n 4p "$OUT/$file" | tr -d '\r')"
}

# C01
save C01-health.txt            "C01 · salud general de la API"       "$API/actuator/health"
save C01-health-liveness.txt   "C01 · sonda de vida (liveness)"      "$API/actuator/health/liveness"
save C01-health-readiness.txt  "C01 · sonda de disponibilidad (readiness)" "$API/actuator/health/readiness"

# C03
save C03-cabeceras-frontend.txt     "C03 · cabeceras del frontend publicado (/login)" "$WEB/login"
save C03-cabeceras-api-health.txt   "C03 · cabeceras de la API (/actuator/health)"    "$API/actuator/health"
save C03-cabeceras-api-protegida.txt "C03 · cabeceras de la API en una ruta protegida sin sesión (/api/v1/orders)" "$API/api/v1/orders"
save C03-api-por-http.txt           "C03 · la API pedida por HTTP sin cifrar (¿redirige a HTTPS?)" "${API/https:/http:}/actuator/health"
save C03-cabeceras-proxy-api.txt    "C03 · cabeceras de la API vista por el proxy del frontend (/api/v1/orders)" "$WEB/api/v1/orders"

# C04
save C04-swagger-ui-html.txt        "C04 · Swagger UI (/swagger-ui.html)"       "$API/swagger-ui.html"
save C04-swagger-ui-index.txt       "C04 · Swagger UI (/swagger-ui/index.html)" "$API/swagger-ui/index.html"
save C04-api-docs.txt               "C04 · contrato OpenAPI (/v3/api-docs)"     "$API/v3/api-docs"
save C04-api-docs-proxy.txt         "C04 · contrato OpenAPI por el proxy del frontend (/v3/api-docs)" "$WEB/v3/api-docs"
