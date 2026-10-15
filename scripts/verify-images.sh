#!/usr/bin/env bash
# Construye las imagenes de produccion y comprueba lo minimo que deben
# cumplir (CAL-17): usuario sin privilegios, perfil prod en el backend y un
# frontend standalone que arranca y sirve /login con el sistema de archivos
# de solo lectura. Lo corre CI en cada push y PR.
#
# Uso: bash scripts/verify-images.sh
# En Git Bash de Windows: MSYS_NO_PATHCONV=1 bash scripts/verify-images.sh
set -euo pipefail
cd "$(dirname "$0")/.."

ref="${VCS_REF:-$(git rev-parse --short HEAD)}"
backend=callejon9-backend:verify
frontend=callejon9-frontend:verify

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

docker build --build-arg VCS_REF="$ref" -t "$backend" backend
docker build --build-arg VCS_REF="$ref" -t "$frontend" frontend

for image in "$backend" "$frontend"; do
    user=$(docker image inspect --format '{{.Config.User}}' "$image")
    [ "$user" = "10001:10001" ] || fail "$image corre como '$user'; se esperaba 10001:10001"
done

profile=$(docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$backend" \
    | grep '^SPRING_PROFILES_ACTIVE=' || true)
[ "$profile" = "SPRING_PROFILES_ACTIVE=prod" ] || fail "$backend tiene '$profile'; se esperaba el perfil prod"

# Mismas restricciones que docker-compose.yml.
container=$(docker run -d --read-only --tmpfs /tmp \
    --tmpfs /app/.next/cache:uid=10001,gid=10001,mode=0700 \
    --cap-drop ALL --security-opt no-new-privileges "$frontend")
trap 'docker rm -f "$container" >/dev/null' EXIT

status=starting
for _ in $(seq 1 45); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$container")
    [ "$status" = healthy ] && break
    sleep 2
done
if [ "$status" != healthy ]; then
    docker logs "$container" >&2
    fail "$frontend no llego a healthy (estado: $status)"
fi

docker exec "$container" node -e \
    "fetch('http://127.0.0.1:3000/login').then(r => process.exit(r.status === 200 ? 0 : 1), () => process.exit(1))" \
    || fail "$frontend no sirve /login"
docker exec "$container" sh -c 'touch /app/.next/cache/escritura' \
    || fail "$frontend no puede escribir en su cache"
if docker exec "$container" sh -c 'touch /app/escritura' 2>/dev/null; then
    fail "$frontend puede modificar /app"
fi

echo "Imagenes verificadas ($ref): usuario 10001, backend en prod, frontend standalone healthy y /app de solo lectura."
