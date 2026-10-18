# Imagen de produccion del frontend (CAL-17).
#
# Base fijada por version y digest: el mismo Dockerfile produce la misma
# base siempre. Para actualizarla, cambia los dos valores juntos y vuelve a
# escanear (ver docs/cal-17-imagenes-produccion.md).
ARG NODE_IMAGE=node:24.21.0-alpine3.24@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1

# --- Dependencias: solo el lockfile decide que se instala -------------------
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
# pnpm sale de corepack con la version de "packageManager" en package.json.
RUN corepack enable pnpm
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# --- Compilacion ------------------------------------------------------------
FROM ${NODE_IMAGE} AS builder
WORKDIR /app
RUN corepack enable pnpm
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Next resuelve los rewrites al compilar y los guarda en la imagen: el
# destino de /api/v1/* queda fijo aqui, no se lee al arrancar. Por defecto es
# el nombre interno "backend", que Compose ya usa; cada entorno debe publicar
# el backend con ese nombre o compilar con otro valor.
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=${BACKEND_ORIGIN} \
    NEXT_OUTPUT=standalone \
    NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1
RUN pnpm build

# --- Ejecucion: solo la salida standalone, sin toolchain --------------------
FROM ${NODE_IMAGE} AS runner
# npm, npx y corepack no hacen falta para servir y traen dependencias con
# vulnerabilidades conocidas.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
        /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
        /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg && \
    addgroup -S -g 10001 nodejs && \
    adduser -S -u 10001 -G nodejs -H -h /nonexistent nextjs

WORKDIR /app
# Los archivos de la aplicacion son de root y de solo lectura para el
# proceso. La unica ruta escribible es la cache de Next.
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
RUN mkdir -p .next/cache && chown nextjs:nodejs .next/cache

# El SSR (api.ts) usa el mismo origen que el rewrite compilado.
ARG BACKEND_ORIGIN=http://backend:8080
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    BACKEND_ORIGIN=${BACKEND_ORIGIN} \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Metadatos al final: un VCS_REF nuevo no invalida la cache de las capas.
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="callejon9-frontend" \
      org.opencontainers.image.source="https://github.com/LudwinGarcia1/callejon9-saas" \
      org.opencontainers.image.revision="${VCS_REF}"

USER 10001:10001
EXPOSE 3000

# Sin curl: Node trae fetch.
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:3000/login').then(r=>process.exit(r.ok?0:1),()=>process.exit(1))"]

# Forma exec: node es PID 1 y recibe SIGTERM directamente.
CMD ["node", "server.js"]
