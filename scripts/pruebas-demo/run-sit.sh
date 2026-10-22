#!/usr/bin/env bash
# Corre las SIT de CAL-138 y CAL-139 dentro de la imagen oficial de Playwright.
# Lo invoca ci/Jenkinsfile.pruebas-demo; también sirve en local (ver README.md).
#
# Espera: el repositorio montado en /repo (solo lectura), la salida en /out,
# C9_TEST_PASSWORD y, opcionales, BASE_URL, HOST_UID y HOST_GID.
set -euo pipefail

: "${C9_TEST_PASSWORD:?Falta C9_TEST_PASSWORD}"
OUT=/out
PEDIDOS="$OUT/CAL-138"
PARIDAD="$OUT/CAL-139"

echo "[SIT] Preparando Playwright y Google Chrome"
mkdir -p /tmp/pw && cd /tmp/pw
npm init -y >/dev/null
npm install --no-audit --no-fund --silent playwright@1.56.1
# Los scripts de evidencia lanzan Chrome estable (channel: "chrome"), no Chromium.
npx playwright install --with-deps chrome >/dev/null
cp /repo/docs/pruebas/sit/CAL-138-pedidos-mesero/playwright/sit-pedidos.mjs \
   /repo/docs/pruebas/sit/CAL-139-app-movil/playwright/capturas-web.mjs \
   /repo/scripts/pruebas-demo/cerrar-orden-a.mjs \
   /repo/scripts/pruebas-demo/verificar.mjs .

# El frontend corre en el plan F1 de App Service, que se suspende sin tráfico: el
# primer acceso puede tardar más que el límite de 30 s de page.goto. Se calienta
# antes de los casos hasta que /login responda 200 en menos de 10 s.
FRONTEND="${BASE_URL:-https://restohub.azurewebsites.net}"
for i in $(seq 1 18); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$FRONTEND/login?slug=pruebas" || true)
    if [ "$code" = "200" ]; then
        echo "[SIT] Frontend listo (intento $i)"
        break
    fi
    echo "[SIT] Calentando el frontend ($i/18, HTTP $code)"
    sleep 10
done

status=0
run() { echo "[SIT] $*"; "$@" || status=1; }

run env OUT_DIR="$PEDIDOS" node sit-pedidos.mjs preparacion
if [ "$status" -eq 0 ]; then
    run env OUT_DIR="$PEDIDOS" node sit-pedidos.mjs casos
fi
# La limpieza corre aunque falle algún caso, para no dejar mesas ocupadas en Azure.
if [ -f "$PEDIDOS/respuestas/estado-ejecucion.json" ]; then
    run env OUT_DIR="$PEDIDOS" node sit-pedidos.mjs limpieza
    run env OUT_DIR="$PEDIDOS" node cerrar-orden-a.mjs
fi
run env OUT_DIR="$PARIDAD" node capturas-web.mjs paridad
run node verificar.mjs "$OUT"

if [ -n "${HOST_UID:-}" ]; then
    chown -R "$HOST_UID:${HOST_GID:-$HOST_UID}" "$OUT" || true
fi
exit "$status"
