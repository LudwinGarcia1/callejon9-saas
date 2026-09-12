---
name: frontend
description: "Implementa, diagnostica o revisa el frontend Next.js/React de Callejón 9: rutas, componentes, TanStack Query, cliente API, tipos, formularios y estados de interfaz."
---

# Frontend

1. Lee `AGENTS.md` y las referencias pertinentes.
2. Localiza la ruta en `frontend/src/app`, reutiliza `components/ui` y mantén componentes cliente solo donde haya interacción.
3. Usa `frontend/src/lib/api.ts`, `endpoints.ts`, `query-keys.ts` y `types.ts`; no dupliques fetch, rutas o claves.
4. El navegador siempre usa `/api/v1/*` relativo y `credentials: include`. No leas ni almacenes la cookie httpOnly.
5. Para mutaciones, define invalidación precisa, feedback con Sonner y estados de envío/error. Incluye loading, vacío, permisos y responsive.
6. Conserva accesibilidad semántica, foco y teclado.
7. Verifica con `pnpm lint`, `pnpm exec tsc --noEmit` y `pnpm build` desde `frontend` según el alcance.

Reporta rutas/componentes cambiados, estados cubiertos y puertas ejecutadas.
