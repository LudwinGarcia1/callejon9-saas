# Evidencia de prueba · CAL-169 y CAL-176 — SIT (re-prueba) · Límites por IP de login y altas

## 1. Identificación

| Campo | Valor |
|---|---|
| Issues | [CAL-169](https://linear.app/callejon19/issue/CAL-169) — Evitar que los logins fallidos de un cliente bloqueen el login de todos en Azure · [CAL-176](https://linear.app/callejon19/issue/CAL-176) — Evitar que cinco altas de un cliente bloqueen el registro de restaurantes para todos en Azure |
| Historia o issue padre | [CAL-118](https://linear.app/callejon19/issue/CAL-118) — SIT · Probar la integración de sistemas en Azure antes de la UAT |
| Pruebas que encontraron los defectos | [CAL-133](../CAL-133-autenticacion/README.md) C05, [CAL-140](../CAL-140-cocina-tiempo-real/README.md) C03 y [CAL-134](../CAL-134-alta-restaurantes-planes/README.md) C02 |
| Tipo de prueba | SIT (re-prueba de defectos) |
| Módulo | Autenticación y alta de restaurantes |
| Entorno | Azure |
| URL del frontend | `https://restohub.azurewebsites.net` |
| URL de la API | `https://app-callejon9-api-prod-3878c6.azurewebsites.net` |
| Commit probado | `68cf1f0` en la API y en restohub, según los últimos registros de despliegue |
| Registro de despliegue | Comentarios «Despliegue» de [CAL-19](https://linear.app/callejon19/issue/CAL-19) (API) y [CAL-16](https://linear.app/callejon19/issue/CAL-16) (frontend) |
| Configuración que activa la corrección | API: `APP_PROXY_SECRET` y `APP_TRUSTED_PROXIES`. restohub: `BACKEND_PROXY_SECRET`. Se comprobó que existen por su nombre, sin leer su valor |
| Clientes | Red 1: laptop del ejecutor, scripts de Node.js por restohub y directo a la API. Red 2: teléfono del ejecutor con datos móviles y sin Wi-Fi, en el navegador |
| Restaurante de prueba | `pruebas` (login y altas rechazadas). El alta desde la red 2 creó `pruebas4` |
| Versión de esta evidencia | v1 · 2026-10-30 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable de los issues | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Red 1 con scripts lanzados por Claude Code; red 2 a mano, en el teléfono |
| Revisor | Pendiente | Distinto del ejecutor. Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`.

| Elemento | Inicio real | Fin real |
|---|---|---|
| C01 — Login | 2026-10-30 19:58 | 2026-10-30 20:04 |
| C02 — Altas | 2026-10-30 20:04 | 2026-10-30 20:08 |

## 4. Alcance y criterios cubiertos

**Objetivo.** Comprobar en Azure que los fallos de login y las altas de un cliente solo frenan a ese cliente, por restohub y directo a la API, después de la corrección del [PR #105](https://github.com/LudwinGarcia1/callejon9-saas/pull/105) y del secreto compartido del [PR #120](https://github.com/LudwinGarcia1/callejon9-saas/pull/120).

**Fuera de alcance.**
- Que un `X-Forwarded-For` inventado no evada el límite ([CAL-106](https://linear.app/callejon19/issue/CAL-106)). Lo cubren las pruebas automatizadas del PR #105, no esta re-prueba.
- El límite de 5 fallos por cuenta: se usaron correos distintos para medir solo el límite por IP.

| Criterio | Caso(s) |
|---|---|
| CAL-169 · 1. Dos clientes en redes distintas: cuando uno falla 20 logins en Azure, el otro inicia sesión con su contraseña correcta | C01 |
| CAL-169 · 2. El cliente que falla más de 20 logins recibe 429 | C01 |
| CAL-169 · 3. En `/api/v1/signup`, el límite de 5 altas por hora se aplica por cliente | C02 |
| CAL-169 · 4. La verificación se hace en Azure, por restohub y directo a la API | C01, C02 |
| CAL-176 · 1. Dos clientes en redes distintas tienen cupos de alta independientes en Azure | C02 |
| CAL-176 · 2. La API identifica la IP real del cliente detrás del proxy del frontend y de Azure, sin confiar en un `X-Forwarded-For` falsificable | C02 (primera parte). La parte de falsificación es del PR #105 |
| CAL-176 · 3. Una prueba automatizada cubre dos IP de cliente distintas detrás del proxy | No es de esta re-prueba: `LoginRateLimitTest` y las pruebas de alta del PR #105 |
| CAL-176 · 4. Re-prueba de CAL-134 C02 paso 4 en Azure: `201` desde otra red mientras la primera está en `429` | C02 |

## 5. Preparación

- [x] `/actuator/health` responde `200 UP`: [`respuestas/health.txt`](respuestas/health.txt).
- [x] La API tiene `APP_PROXY_SECRET` y `APP_TRUSTED_PROXIES`, y restohub tiene `BACKEND_PROXY_SECRET` (consulta con `az webapp config appsettings list`, solo nombres).
- [x] Desde la red 1 no hubo logins fallidos ni altas en los 60 minutos anteriores.

**Datos de prueba**

| Dato | Valor | Cómo se creó |
|---|---|---|
| Login fallido | `ip-<hora>-<n>@noexiste.test`, un correo distinto por intento, en el restaurante `pruebas` | Correos inexistentes: no bloquean cuentas reales |
| Login correcto en la red 2 | `mesero@pruebas.com` | Semilla del restaurante `pruebas` |
| Alta rechazada | Slug `pruebas`, ya ocupado, con una contraseña aleatoria que no se guardó | El 409 llega después de consumir cupo, así que no crea nada |
| Alta aceptada en la red 2 | Restaurante `pruebas4` | Formulario de alta de restohub en el teléfono |

## 6. Casos de prueba

### C01 — Veinte logins fallidos frenan solo a la red que los hizo

| Campo | Valor |
|---|---|
| Criterios cubiertos | CAL-169 · 1, 2 y 4 |
| Inicio / fin | 2026-10-30 19:58 / 2026-10-30 20:04 |
| Estado | ✅ Aprobado |

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Red 1: 21 logins por restohub, cada uno con un correo inexistente distinto | 401 en los 20 primeros y 429 en el 21 | 19:58:35 – 19:58:41: 401 del 1 al 20 y **429** en el 21, con `Retry-After: 895` | [`C01-login-por-red.txt`](respuestas/C01-login-por-red.txt) |
| 2 | Red 1: un login fallido directo a la API | 429, porque es la misma red | 19:58:41: **429**, con `Retry-After: 894` | Mismo archivo |
| 3 | Red 2: el teléfono entra a `https://restohub.azurewebsites.net/login?slug=pruebas` con `mesero@pruebas.com` | Entra | **Entra sin bloqueo**, según informa el ejecutor | Sin captura |
| 4 | Red 1, después del paso 3: un login fallido por restohub y otro directo | 429 por las dos rutas: el bloqueo de la red 1 seguía activo durante el paso 3 | 20:04:26: **429** por las dos rutas, con `Retry-After: 550` | [`C01-login-por-red.txt`](respuestas/C01-login-por-red.txt) |

**Observaciones.** En la SIT original de CAL-133 C05, el teléfono de otra red quedó bloqueado en este mismo paso. Ahora entra. El paso 4 descarta que el teléfono haya entrado solo porque ya había vencido la ventana.

### C02 — Cinco altas agotan solo el cupo de la red que las hizo

| Campo | Valor |
|---|---|
| Criterios cubiertos | CAL-169 · 3 y 4; CAL-176 · 1, 2 y 4 |
| Inicio / fin | 2026-10-30 20:04 / 2026-10-30 20:08 |
| Estado | ✅ Aprobado |

| # | Acción | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| 1 | Red 1: 6 altas por restohub con el slug ocupado `pruebas` | 409 en las 5 primeras y 429 en la sexta | 20:04:27 – 20:04:28: **409** «Ya existe un restaurante con el identificador 'pruebas'» del 1 al 5, y **429** en la 6, con `Retry-After: 3599` | [`C02-altas-por-red.txt`](respuestas/C02-altas-por-red.txt) |
| 2 | Red 1: un alta directo a la API | 429 | 20:04:29: **429**, con `Retry-After: 3599` | Mismo archivo |
| 3 | Red 2: el teléfono da de alta un restaurante desde `/signup` | 201 | **El alta se completó** y creó `pruebas4`, según informa el ejecutor | Sin captura |
| 4 | Red 1, después del paso 3: un alta por restohub y otra directo | 429 por las dos rutas | 20:08:24: **429** por las dos rutas, con `Retry-After: 3363` | [`C02-altas-por-red.txt`](respuestas/C02-altas-por-red.txt) |

**Observaciones.** El alta del paso 3 creó un restaurante real en producción, `pruebas4`. No se borró, porque la interfaz no permite eliminar restaurantes (el panel de plataforma ni siquiera los lista, [CAL-177](https://linear.app/callejon19/issue/CAL-177)). Queda como dato de prueba.

## 7. Defectos encontrados

Ninguno nuevo. Re-probados:

| ID | Caso | Severidad | Estado | Re-probado el |
|---|---|---|---|---|
| CAL-169 (y su duplicado CAL-167) | C01, C02 | Crítica | Corregido en Azure | 2026-10-30 |
| CAL-176 | C02 | Alta | Corregido en Azure | 2026-10-30 |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 2 | 2 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**
- Los pasos de la red 2 (C01 paso 3 y C02 paso 3) se registran según lo que informó el ejecutor, sin captura. Los pasos 4 de cada caso, que sí tienen respuesta literal, prueban que la red 1 seguía bloqueada en ese momento.
- La red 2 se probó solo por restohub, desde el navegador. Por la ruta directa, la API se probó solo desde la red 1.
- No se registraron el modelo del teléfono ni su navegador.

**Conclusión del ejecutor.** Los criterios de CAL-169 y CAL-176 que dependen de Azure se cumplen contra `68cf1f0`. Los fallos de login y las altas de la red 1 la bloquearon por restohub y directo a la API, mientras la red 2 iniciaba sesión y daba de alta un restaurante sin bloqueo. Para pasar los issues a Done faltan la revisión independiente del PR #105, registrada en `workflow/state/review-history.jsonl`, y la revisión de esta evidencia.

## 9. Anexo SIT

| Interfaz verificada | Caso(s) | Evidencia clave |
|---|---|---|
| Navegador → restohub (middleware de Next con `BACKEND_PROXY_SECRET`) → API | C01, C02 | 429 para la red 1 y acceso para la red 2 por la misma ruta |
| Cliente → App Service de la API (`X-Forwarded-For` de Azure) | C01, C02 | 429 directo para la red 1, con el mismo `Retry-After` que por restohub |

## 10. Revisión

*La llena el revisor, que no puede ser quien ejecutó la prueba.*

- [ ] Cada criterio de los dos issues tiene al menos un caso con resultado
- [ ] Las respuestas HTTP coinciden con el comportamiento real del código (rutas, códigos y mensajes)
- [ ] El commit probado corresponde al registro de despliegue
- [ ] No hay contraseñas, tokens, cookies, direcciones IP ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados. Las respuestas no fueron editadas, salvo la hora del encabezado de `health.txt`, que se pasó de UTC a `America/Mexico_City`. Los pasos sin captura se marcan como tales.

**Ejecutor:** Braulio Hernández Mejía · **Fecha:** 2026-10-30
