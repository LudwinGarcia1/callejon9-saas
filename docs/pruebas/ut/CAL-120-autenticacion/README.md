# Evidencia de prueba · CAL-120 — UT · Autenticación y sesiones

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-120](https://linear.app/callejon19/issue/CAL-120/ut-autenticacion-y-sesiones) — UT · Autenticación y sesiones |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | Autenticación y sesiones (`auth/throttle/ClientIp`, `auth/service/RefreshTokenService`, `frontend/src/app/login`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia; pruebas ejecutadas sobre `4531dc9` (rama desde `main` `68e6e42`) |
| Ejecución de CI | Checks del PR |
| Navegador / dispositivo | No aplica: JDK 25 con `--release 21`, Maven y Testcontainers (PostgreSQL 16 en Docker 29.7.2); Node 24 con Vitest 3 y jsdom |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Pruebas, mutaciones y corrección de `ClientIp` de este PR |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Esta tarea (CAL-120) | | | 2026-10-21 15:43 | | — |
| Ejecución de las pruebas | 2026-10-21 | 2026-10-21 | 2026-10-21 15:54 | 2026-10-21 16:07 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Cubrir con pruebas automatizadas tres reglas de la autenticación que no tenían prueba: la IP del cliente cuando App Service agrega el puerto, el vencimiento exacto de la sesión de 7 días y el borrado de la contraseña tras un login fallido.

**Fuera de alcance.** En quién confía `ClientIp` (la dirección interna 169.254.x.x de App Service es CAL-167; las cabeceras inventadas, CAL-106) y el aviso falso de «sesión expirada» tras un login fallido (CAL-168).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| `ClientIp` con `X-Forwarded-For` que trae puerto (`ip:puerto`), como llega desde App Service | C01 |
| Refresh token al límite de la vida de la sesión (7 días): rechazo exacto al vencer | C02 |
| Formulario de login: la contraseña se borra del campo tras un intento fallido | C03 |
| Criterio de salida: casos válido, inválido y de borde; `mvnw verify` y `pnpm test` en verde | C01 a C04 |

## 5. Preparación

- [x] Docker Desktop encendido para las pruebas de integración del backend (Testcontainers)
- [x] `RefreshTokenService` recibe un `Clock` (por defecto `Clock.systemUTC()`, el mismo patrón de `LoginAttemptLimiter`), para fijar la hora en la prueba del vencimiento; el comportamiento no cambia
- [x] Formulario de login montado con `@testing-library/react`, con `next/navigation` y `api.post` simulados

## 6. Casos de prueba

### C01 — `ClientIp` quita el puerto que agrega App Service

| Campo | Valor |
|---|---|
| Criterio cubierto | `ClientIp` con puerto |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:54 / 2026-10-21 15:56 |
| Estado | ✅ Aprobado (tras corregir el defecto) |
| Defecto vinculado | [CAL-173](https://linear.app/callejon19/issue/CAL-173) (corregido en este PR) |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Correr las pruebas nuevas contra el código de `main` | `203.0.113.7:51234`, `[2001:db8::7]:51234`, `:51234` | Sin puerto | **4 de 7 fallan**: devuelve `203.0.113.7:51234`, `[2001:db8::7]:51234` y `:51234` | [`clientip-antes-de-corregir.txt`](respuestas/clientip-antes-de-corregir.txt) |
| 2 | Corregir `ClientIp` (`withoutPort`) y volver a correr | Los mismos casos | IPv4 e IPv6 sin puerto; una IPv6 sin corchetes queda intacta; una entrada vacía o solo con puerto cae a la dirección de la conexión | **7 de 7** aprobadas, incluidas las tres originales | [`backend-verify.txt`](respuestas/backend-verify.txt) |
| 3 | Comparar dos conexiones del mismo cliente | Puertos 51234 y 51999 | La misma llave | Misma llave (`203.0.113.7`) | Prueba `sameClientDifferentPortsShareTheKey` |

**Observaciones.** Con el puerto, cada conexión de un mismo cliente tenía una llave distinta y el límite por IP no lo frenaba. Hoy no se manifiesta en Azure porque `ClientIp` ni siquiera lee la cabecera ahí (CAL-167), pero aparecería en cuanto se corrija CAL-167.

### C02 — La sesión vence exactamente a los 7 días y renovar no la alarga

| Campo | Valor |
|---|---|
| Criterio cubierto | Vida de la sesión |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 15:57 / 2026-10-21 15:58 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

**Precondición.** Reloj fijo; login el 2026-10-14 18:00 UTC; vencimiento esperado el 2026-10-21 18:00 UTC.

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Iniciar sesión | — | Token y fila guardada con vencimiento = login + 7 días | Vencimiento exacto 2026-10-21T18:00:00Z en la respuesta, en el JWT y en la fila | `loginOpensASevenDaySession` |
| 2 | Renovar 1 ns antes del vencimiento | — | Renueva; el token nuevo hereda el mismo vencimiento y la misma familia | Aprobado | `renewsJustBeforeTheEndWithoutExtendingTheSession` |
| 3 | Renovar en el instante exacto del vencimiento | — | Rechazo «Sesion no renovable.», sin consumir el token | Aprobado | `rejectsExactlyAtTheEnd` |
| 4 | Renovar 1 s después | — | Rechazo | Aprobado | `rejectsAfterTheEnd` |
| 5 | Renovar al tercer día | — | El vencimiento sigue siendo el del login | Aprobado | `renewingMidSessionKeepsTheOriginalEnd` |

### C03 — El formulario de login borra la contraseña tras un intento fallido

| Campo | Valor |
|---|---|
| Criterio cubierto | Borrado de la contraseña |
| Prioridad | Media |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:00 / 2026-10-21 16:03 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Login rechazado con 401 | `mesero@pruebas.com` | Contraseña vacía; restaurante y correo conservados; «Restaurante, correo o contraseña incorrectos.» | Aprobado | [`frontend.txt`](respuestas/frontend.txt) |
| 2 | Login rechazado con 429 | `detail` del bloqueo | Contraseña vacía y aviso «Demasiados intentos» con el detalle del servidor | Aprobado | |
| 3 | Validación local fallida (sin restaurante) | — | No envía nada y **conserva** la contraseña | Aprobado | |
| 4 | Login correcto | Rol WAITER | Entra a `/waiter` | Aprobado | |

### C04 — Puertas completas del backend y del frontend

| Campo | Valor |
|---|---|
| Criterio cubierto | Criterio de salida |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:03 / 2026-10-21 16:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `.\mvnw.cmd -B verify` | PostgreSQL 16 con Testcontainers | Todas las pruebas en verde | **633 de 633** aprobadas. La etapa SpotBugs no corre en local con JDK 25 (ver observaciones) | [`backend-verify.txt`](respuestas/backend-verify.txt) |
| 2 | `pnpm test`, `pnpm lint`, `pnpm exec tsc --noEmit` | — | Sin fallas | **156 de 156** pruebas; lint y tipos sin errores | [`frontend.txt`](respuestas/frontend.txt) |

**Observaciones.** SpotBugs 4.8.6 no lee las clases del JDK 25 («Unsupported class file major version 69»). El proyecto usa Java 21, que es lo que corre en CI; esa etapa se valida con los checks del PR.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-173](https://linear.app/callejon19/issue/CAL-173) | C01 | Alta | `ClientIp` conservaba el puerto de `X-Forwarded-For`, así que un mismo cliente tenía una llave distinta por conexión | Corregido en este PR | 2026-10-21 15:56 |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 4 | 4 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- El issue era solo de pruebas, pero las pruebas de C01 encontraron un defecto; se corrigió aquí por ser un cambio acotado (`ClientIp.withoutPort`) y se registró como CAL-173.
- `RefreshTokenService` recibió un `Clock` inyectable para poder probar el instante exacto del vencimiento.
- La etapa SpotBugs se valida en CI, como explica C04.

**Conclusión del ejecutor.** Los tres criterios de CAL-120 quedan cubiertos con pruebas que fallan sin el comportamiento que protegen. La sesión vence exactamente a los 7 días del login y renovar no la alarga; la contraseña se borra tras un login fallido; y `ClientIp` ya quita el puerto que agrega App Service, un defecto que las pruebas encontraron y que este PR corrige.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/.../auth/throttle/ClientIpTest.java` (4 nuevas, 7 en total), `backend/.../auth/service/RefreshTokenSessionExpiryTest.java` (5), `frontend/src/test/login-form.test.tsx` (4) |
| Comando local | `cd backend; .\mvnw.cmd -B verify` y `cd frontend; pnpm test; pnpm lint; pnpm exec tsc --noEmit` |
| Resultado local | Backend: 633 pruebas en verde. Frontend: 156 pruebas, lint y tipos en verde |
| Resultado en CI | Checks del PR |
| Prueba de que fallan sin el comportamiento | Cuatro mutaciones, todas detectadas: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Pruebas que fallan |
|---|---|
| M1 · `ClientIp` conserva el puerto | 4 |
| M2 · se renueva en el instante exacto del vencimiento | 1 |
| M3 · renovar alarga la sesión | 1 |
| M4 · el formulario conserva la contraseña tras un fallo | 2 |

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las pruebas fallan sin el comportamiento que protegen (sección 9)
- [ ] La corrección de `ClientIp` no cambia en quién confía la clase
- [ ] Los checks del PR pasan, incluido SpotBugs con Java 21
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las salidas no fueron editadas salvo para ocultar secretos y rutas locales, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** 2026-10-21
