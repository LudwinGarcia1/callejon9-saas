# Runtimes y límites

| Runtime | Entrada | Puerto local | Responsabilidad |
|---|---|---:|---|
| Navegador | `frontend/src/app` | 3000 | UI, estado cliente y cookie first-party opaca |
| Next.js 15 | `frontend/next.config.ts` | 3000 | App Router y rewrite `/api/v1/*` |
| Spring Boot 3.4 | `backend/src/main/java/com/callejon9` | 8080 | HTTP, autorización, negocio, transacciones y STOMP |
| PostgreSQL 16 | migraciones en `backend/src/main/resources/db/migration` | 5433 local / 5432 CI | esquema, integridad y aislamiento RLS |

El navegador no cruza directamente al backend. `BACKEND_ORIGIN` es server-only y por defecto apunta a `http://localhost:8080`. La aplicación usa el rol `callejon9_app`; Flyway usa `callejon9_owner`.
