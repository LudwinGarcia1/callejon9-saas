# Evidencia de prueba · CAL-126 — UT · App móvil de meseros

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-126](https://linear.app/callejon19/issue/CAL-126/ut-app-movil-de-meseros) — UT · App móvil de meseros |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | App móvil de meseros (`mobile/src/api/client.ts`, `mobile/src/config.ts`, `mobile/src/api/auth.ts`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia, sobre `main` `68e6e42` |
| Ejecución de CI | Checks del PR, incluido el job nuevo `mobile` |
| Navegador / dispositivo | No aplica: Node 24 con Vitest 5 |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Pruebas, mutaciones y job de CI de este PR |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Historia / issue padre (CAL-119) | | | | | |
| Esta tarea (CAL-126) | | | 2026-10-21 15:44 | | Reabierta en revisión: la suite anterior no probaba la lógica del issue |
| Ejecución de las pruebas | 2026-10-21 | 2026-10-21 | 2026-10-21 15:47 | 2026-10-21 15:51 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Proteger con pruebas automatizadas el cliente HTTP de la app móvil: la traducción de cada código de error, la renovación de sesión ante un 401, el tiempo límite, el rechazo de `http://` fuera de desarrollo y el mensaje único del login.

**Fuera de alcance.** Las pantallas de React Native (se probaron de punta a punta en la SIT, CAL-139) y la app compilada para tienda.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| Configurar el ejecutor de pruebas y un script `pnpm test` en `mobile/` | C01 |
| `client.ts`: traducción de 400, 401, 403, 404, 409, 429 y 5xx a `ApiError` | C02 |
| `client.ts`: una sola renovación ante 401 | C03 |
| `client.ts`: tiempo límite de 15 s | C04 |
| `config.ts`: rechazo de URLs `http://` fuera de desarrollo | C05 |
| `auth.ts`: mensaje único ante 401 | C06 |
| Criterio de salida: casos válido, inválido y de borde por regla; la suite corre en CI | C01 a C06, sección 9 |

## 5. Preparación

- [x] `mobile/` con Vitest 5 y `pnpm test` (agregado por el PR #64)
- [x] `vitest.config.mts`: `__DEV__` pasa de `define` a un archivo de arranque (`src/test/setup.ts`), para que una prueba pueda simular una build de producción
- [x] `fetch` simulado con `vi.stubGlobal`; cada prueba recarga el módulo con `vi.resetModules` y su propio `EXPO_PUBLIC_API_URL`

## 6. Casos de prueba

Los resultados por prueba están en [`respuestas/pnpm-test.txt`](respuestas/pnpm-test.txt) (33 pruebas, todas aprobadas).

### C01 — La suite corre con `pnpm test` en local y en CI

| Campo | Valor |
|---|---|
| Criterio cubierto | Configuración y CI |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `pnpm exec vitest run --reporter=verbose` en `mobile/` | 3 archivos de prueba | Todas en verde | 33 de 33 aprobadas en 263 ms | [`pnpm-test.txt`](respuestas/pnpm-test.txt) |
| 2 | `pnpm exec tsc --noEmit` en `mobile/` | — | Sin errores de tipos | Exit 0 | [`pnpm-test.txt`](respuestas/pnpm-test.txt) |
| 3 | Agregar la suite al CI | `.github/workflows/ci.yml` y `Jenkinsfile` | Un job que instala, revisa tipos y corre las pruebas de `mobile/` | Job `mobile` en GitHub Actions y etapa `Mobile Quality Gates` en Jenkins | Checks del PR |

**Observaciones.** El issue pedía Jest con `jest-expo`. Se conservó Vitest, que ya estaba configurado: los módulos cubiertos son TypeScript sin componentes de React Native, y Vitest los prueba sin el preset de Expo.

### C02 — Cada código HTTP se traduce a un `ApiError` con un mensaje claro

| Campo | Valor |
|---|---|
| Criterio cubierto | Traducción de errores |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | 400 con `problem+json` | `detail` y `errors` | `validation` con el detalle y los errores por campo | Aprobado | `client: traducción de errores HTTP` |
| 2 | 400 sin cuerpo | — | `validation` con «Revisa los datos enviados.» | Aprobado | |
| 3 | 403 | `detail` interno | `forbidden` con mensaje propio, sin exponer el detalle del servidor | Aprobado | |
| 4 | 404 con y sin cuerpo | — | `notFound` con el detalle o el mensaje por defecto | Aprobado | |
| 5 | 409 | `detail` de mesa ocupada | `conflict` con el detalle | Aprobado | |
| 6 | 429 con y sin cuerpo | — | `throttled` con el detalle o «Demasiados intentos. Espera unos minutos.» | Aprobado | |
| 7 | 500 y 503 con cuerpo que no es JSON | — | `server` con «El servidor tuvo un problema. Intenta más tarde.» | Aprobado | |
| 8 | Falla de red | `TypeError` de `fetch` | `network` con «No se pudo conectar con el servidor…» | Aprobado | `client: transporte` |
| 9 | Respuesta correcta y 204 | — | Devuelve el JSON; `undefined` en 204 | Aprobado | |

### C03 — Un 401 provoca una sola renovación de sesión

| Campo | Valor |
|---|---|
| Criterio cubierto | Renovación ante 401 |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | 401 y renovación correcta | 401 → `POST /auth/refresh` 204 → 200 | Repite la petición y devuelve sus datos | Aprobado: exactamente `GET`, `POST /auth/refresh`, `GET` | `client: renovación de la sesión ante un 401` |
| 2 | 401 y renovación rechazada | 401 → 401 | `unauthorized` y aviso de sesión expirada; no repite | Aprobado | |
| 3 | 401 y la red falla al renovar | 401 → `TypeError` | Igual que una renovación fallida | Aprobado | |
| 4 | Dos peticiones reciben 401 a la vez | `/tables` y `/products` | Una sola llamada a `/auth/refresh`, y ambas se completan | Aprobado | |
| 5 | Petición con `skipRefresh` | `/auth/logout` | No renueva ni avisa expiración | Aprobado | |

### C04 — Una petición se corta a los 15 segundos

| Campo | Valor |
|---|---|
| Criterio cubierto | Tiempo límite |
| Prioridad | Media |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Petición que nunca responde, con temporizadores simulados | 14 999 ms y luego 1 ms | Sigue viva a los 14 999 ms y a los 15 000 ms se corta como `timeout` | Aprobado | `client: transporte` |

### C05 — La app solo usa `http://` en desarrollo y si se habilita

| Campo | Valor |
|---|---|
| Criterio cubierto | Rechazo de `http://` fuera de desarrollo |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | URL `https://` con diagonales al final | `https://api.ejemplo.mx//` | Se acepta sin diagonales | Aprobado | `config: URL del servidor` |
| 2 | Sin URL | Espacios | Error «Falta EXPO_PUBLIC_API_URL…» | Aprobado | |
| 3 | `http://` en desarrollo sin permiso | `ALLOW_INSECURE_HTTP=false` | Rechazo | Aprobado | |
| 4 | `http://` en desarrollo con permiso | `ALLOW_INSECURE_HTTP=true` | Se acepta | Aprobado | |
| 5 | `http://` en producción con permiso | `__DEV__ = false` | Rechazo | Aprobado | |
| 6 | Otro esquema | `ftp://` | Rechazo | Aprobado | |
| 7 | Configuración inválida | `http://` | `client` no llama a `fetch` y lanza `config` | Aprobado | `client: transporte` |

### C06 — El login muestra un único mensaje ante credenciales incorrectas

| Campo | Valor |
|---|---|
| Criterio cubierto | Mensaje único ante 401 |
| Prioridad | Media |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:51 / 2026-10-21 15:51 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Login correcto | 200 con el usuario | Devuelve el usuario y envía las credenciales a `/auth/login` | Aprobado | `auth: login` |
| 2 | Login con 401 | — | «Restaurante, correo o contraseña incorrectos.», una sola llamada, sin renovar ni avisar expiración | Aprobado | |
| 3 | Login con 429 | `detail` del bloqueo | Conserva el mensaje del servidor | Aprobado | |
| 4 | Logout | 401 o falla de red | Termina sin error y sin renovar | Aprobado | `auth: logout` |
| 5 | Sesión guardada caducada | `/auth/me` 401 → refresh → 200 | Recupera al usuario | Aprobado | `auth: sesión guardada` |

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Ninguno | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 6 | 6 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- Se usó Vitest en lugar de Jest con `jest-expo`, como explica C01.
- La ejecución en CI se verifica con los checks del PR, porque el job `mobile` es nuevo en este cambio.

**Conclusión del ejecutor.** Los criterios de CAL-126 se cumplen. Las 33 pruebas cubren la traducción de errores, la renovación única ante 401, el tiempo límite, el rechazo de `http://` fuera de desarrollo y el mensaje del login, y siete reglas rotas a propósito fueron detectadas. La suite corre ahora en GitHub Actions y en Jenkins.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `mobile/src/test/client.test.ts` (20), `mobile/src/test/config.test.ts` (7), `mobile/src/test/auth.test.ts` (6, en 3 grupos) |
| Comando local | `cd mobile; pnpm test` y `pnpm exec tsc --noEmit` |
| Resultado local | «Tests 33 passed (33)»; `tsc` sin errores |
| Resultado en CI | Job `mobile` de GitHub Actions en el PR |
| Prueba de que fallan sin el comportamiento | Siete mutaciones, todas detectadas: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Pruebas que fallan |
|---|---|
| M1 · `config` acepta `http://` siempre | 3 |
| M2 · la excepción de `http://` también vale en producción | 1 |
| M3 · cada 401 hace su propia renovación | 1 |
| M4 · el tiempo límite real no es de 15 s | 1 |
| M5 · un 429 se reporta como error del servidor | 3 |
| M6 · no avisa cuando la sesión no se puede renovar | 2 |
| M7 · el login deja pasar el mensaje genérico de sesión | 1 |

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las pruebas fallan sin el comportamiento que protegen (sección 9)
- [ ] El job `mobile` pasa en CI
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las salidas no fueron editadas salvo para ocultar secretos, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** 2026-10-21
