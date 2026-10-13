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

## Cobertura de cierre

`error-ui.test.tsx` prueba detail RFC7807, errores por campo, prioridad de
validación local, texto sin interpretar HTML y estados loading/error/vacío.
`order-mutations.test.tsx` monta la vista real y prueba agregar productos,
enviar a cocina y cancelar. Usa API y navegación simuladas y sustituye el
panel de selección por controles mínimos: prueba la mutación de la vista,
no el comportamiento interno del selector. Verifica loading, botón deshabilitado
ante segundo clic, carrito conservado al fallar, carrito limpio al completar,
respuesta guardada e invalidación de órdenes. La cancelación conserva su
invalidación de mesas y retorno al listado; la prueba verifica navegación y órdenes.

No se prueba idempotencia del backend ni todos los recorridos posibles:
doble envío aquí significa otro clic mientras la mutación está pendiente.
Los recorridos completos y la interacción del selector corresponden a CAL-28.

Coverage v8 focalizado en siete módulos (API, keys, tema, sesión, FieldError,
QueryState y OrderView): 89,90 % líneas/statements, 83,19 % ramas y
73,77 % funciones. Umbrales obligatorios: 85/75/70/85 para
statements/branches/functions/lines. No representa cobertura global del producto.

El 2026-10-08 se demostraron seis regresiones temporales detectadas con exit 1:
perder errors RFC7807, romper el prefijo de claves de orden, quitar límites de
branding, omitir limpieza de sesión, ocultar error de campo y habilitar el botón
durante envío. Cada archivo se restauró en finally antes de las puertas finales.

Ejecutar desde `frontend`: `pnpm test`, `pnpm lint`,
`pnpm exec tsc --noEmit` y `pnpm build`. `pnpm test:coverage` verifica los
umbrales. El job `frontend-tests` del workflow actual ejecuta instalación
con lockfile congelado y test:coverage en Node 24, y publica coverage.
La ejecución operativa Jenkins sigue correspondiendo a CAL-87.
