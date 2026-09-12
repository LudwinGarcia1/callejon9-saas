# Callejón 9 — guía para agentes

Este repositorio contiene un SaaS multi-restaurante. La garantía central es que PostgreSQL impone el aislamiento entre restaurantes mediante Row Level Security (RLS); ninguna mejora funcional justifica debilitarla.

## Autoridad y contexto

1. Este archivo gobierna todo el repositorio.
2. Para una tarea concreta, el issue/brief aceptado y `workflow/product/` definen intención y alcance.
3. El código, las migraciones Flyway y las pruebas representan el comportamiento implementado.
4. Las especialidades de `.agents/skills/*/references/` resumen el proyecto, pero deben contrastarse con los archivos reales antes de decidir.

Lee solo el contexto necesario. Empieza por `workflow/README.md`, consulta `workflow/project-config.yaml` y usa las skills del flujo (`wf-*`, `pm-*`), que se invocan con `$nombre` en Codex y con `/nombre` en Claude Code.

## Invariantes no negociables

- RLS es la frontera de seguridad. `callejon9_app` no debe ser propietario de tablas ni tener `BYPASSRLS` o privilegios de superusuario.
- Las 14 tablas de datos por restaurante conservan `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, `USING` y `WITH CHECK`.
- El tenant proviene de la cookie JWT firmada y se publica con `set_config('app.tenant_id', ..., true)` dentro de cada transacción. Nunca aceptes `tenant_id` del cliente como autoridad.
- El navegador llama rutas relativas `/api/v1/*`; `frontend/next.config.ts` las reescribe al backend. No introduzcas llamadas directas a `:8080`, CORS innecesario ni almacenamiento del token en JavaScript.
- Las migraciones son append-only. No reescribas una migración aplicada: agrega una nueva `V{n}__descripcion.sql`.
- El código fuente y sus identificadores van en inglés; interfaz y documentación, en español.
- En frontend usa exclusivamente `pnpm` y conserva `frontend/pnpm-lock.yaml`.
- No incluyas contraseñas, tokens, secretos ni datos personales en commits, documentación o configuración del harness.

## Convenciones del proyecto

- Backend: Java 21, Spring Boot 3.4, paquetes por funcionalidad bajo `backend/src/main/java/com/callejon9`. Los controllers traducen HTTP; los services contienen negocio y límites transaccionales; los repositories usan Spring Data JPA.
- API: rutas `/api/v1`, errores RFC 7807 y autorización por rol en el backend.
- Frontend: Next.js 15 App Router, React 19, TypeScript estricto, TanStack Query, Tailwind CSS 4 y shadcn/ui. Centraliza transporte, endpoints, query keys y tipos en `frontend/src/lib`.
- Diseño: reutiliza los tokens de `frontend/src/app/globals.css`, el tema de `frontend/src/lib/tenant-theme.ts` y los componentes de `frontend/src/components/ui`.
- Git: `main` es la base. Usa ramas `feat/*`, `fix/*`, `docs/*` o `chore/*`; no hagas push, merge, publicación ni cambios externos salvo petición explícita. Commits convencionales y, cuando exista, referencia del issue.

## Verificación proporcional

Ejecuta la comprobación más estrecha durante el desarrollo y cierra con las puertas afectadas:

- Backend: `cd backend; .\mvnw.cmd -B verify`
- Frontend lint: `cd frontend; pnpm lint`
- Frontend tipos: `cd frontend; pnpm exec tsc --noEmit`
- Frontend producción: `cd frontend; pnpm build`
- RLS manual: usa `scripts/verify-rls.sql` como `callejon9_app` contra una base local preparada.

Las pruebas del backend requieren PostgreSQL real y las variables descritas en `README.md`; H2 no demuestra RLS. No afirmes que una puerta pasó si no se ejecutó. Registra bloqueos y evidencia verificable.

## Linear y coordinación

La conexión de proyecto se llama `linear_callejon9`. En Codex se declara en `.codex/config.toml` y se autentica con `codex mcp login linear_callejon9`; en Claude Code se declara en `.mcp.json` y se autentica con `/mcp`. Hasta que `workflow/project-config.yaml` tenga workspace y team reales y `enabled: true`, los flujos PM operan en modo local y no crean ni modifican issues.

Los especialistas de `.agents/agents/` son opcionales y cada herramienta los expone a su manera: Codex desde `.codex/agents/*.toml`, Claude Code desde `.claude/agents/*.md`. Úsalos solo cuando el usuario pida explícitamente delegación o trabajo paralelo; el agente principal integra y verifica el resultado.

## Definición de terminado

Una entrega está terminada cuando cumple el alcance aceptado, conserva los invariantes, añade o actualiza pruebas relevantes, pasa las puertas aplicables, actualiza la documentación afectada y deja trazabilidad honesta en el issue o en `workflow/state/review-history.jsonl`.
