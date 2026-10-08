# CAL-35 — pruebas locales de frontend

Vitest reutiliza el alias existente. Las pruebas de lógica usan Node; el hook
de sesión usa jsdom 26 y Testing Library, con React y TanStack Query reales.
No se realizan solicitudes de red: API y navegación se sustituyen solo en
sus límites. Cada prueba crea y limpia su propio QueryClient; las promesas
controladas permiten observar loading y logout antes de resolver la petición.

## Cobertura implementada

- API: detail/errors RFC7807, fallback no JSON, cookies, serialización,
  filtros y fallos de transporte.
- Caché: separación de filtros, rangos e identificadores e invalidación por
  prefijo de órdenes sin invalidar mesas.
- Branding: límites de hue 0–360 y chroma 0–0,4; los valores no numéricos o
  no finitos usan defaults. La fuente proviene de la lista cerrada y rechaza
  claves heredadas. No se insertan fragmentos CSS procedentes de valores inválidos.
- Sesión: loading, lectura correcta, 401 sin reintentos, logout aprobado o
  fallido con limpieza de caché y salida a login. La navegación simulada
  desmonta el consumidor como lo hace un cambio de pantalla real.

Las pruebas de componente cubren efectos y estados observables. Los recorridos
completos en navegador corresponden a CAL-28. No se usan snapshots masivos.

## Pendientes

CAL-35 sigue en progreso: faltan errores RFC7807 en UI, mutaciones de orden
(loading, doble envío, error e invalidación), coverage focalizado y evidencia
de fallos inducidos. Tener estas pruebas integradas no cierra el issue.

Ejecutar desde `frontend`: `pnpm test`, `pnpm lint`,
`pnpm exec tsc --noEmit` y `pnpm build`. El workflow actual solo comprueba
backend; las puertas frontend de CI corresponden a CAL-87.
