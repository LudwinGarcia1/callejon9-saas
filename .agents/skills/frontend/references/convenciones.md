# Convenciones frontend

- Rutas y layouts: `frontend/src/app`; cada pantalla separa `page.tsx` de su vista interactiva cuando corresponde.
- Componentes reutilizables: `frontend/src/components/ui`, `shared`, `layout` y `charts`.
- Alias: `@/components`, `@/lib`, `@/hooks` definidos en `frontend/components.json` y `tsconfig.json`.
- Usa Server Components por defecto; agrega `"use client"` solo por hooks, eventos o APIs del navegador.
- Mantén tipos compartidos de transporte en `src/lib/types.ts`, formato en `format.ts` y clases con `cn` de `utils.ts`.
- No introduzcas un state manager global si TanStack Query y estado local resuelven el caso.
- Los mensajes al usuario van en español y deben explicar cómo recuperarse del error.
