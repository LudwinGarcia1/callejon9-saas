# Evidencia de prueba · CAL-121 — UT · Alta de restaurantes y planes

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10). Defectos registrados en Linear: CAL-178 (C04, abierto) y CAL-179 (C05, corregido en este PR y re-probado).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-121](https://linear.app/callejon19/issue/CAL-121/ut-alta-de-restaurantes-y-planes) — UT · Alta de restaurantes y planes |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | Alta de restaurantes y planes (`platform/tenant/web/dto/SignupRequest`, `platform/tenant/service/PlanLimitService`, `table/service/TableService`, `frontend/src/app/signup/signup-view.tsx`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia; pruebas ejecutadas en la rama `test/cal-121-ut-alta-restaurantes-planes` desde `main` `d31fd0c`, con los cambios aún sin commit |
| Ejecución de CI | Checks del PR |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Herramientas | JDK 21.0.9 (como CI), Maven Wrapper, JUnit 5, Mockito y Testcontainers con PostgreSQL 16 en Docker 29.8.2; Node 22.19.0 con Vitest 3.2.7, jsdom y Testing Library; Microsoft Edge 155 para comprobar el `pattern` en un navegador real |
| Versión de esta evidencia | v1 · 2026-10-22 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Pruebas, mutaciones, registro de CAL-178 y CAL-179, y corrección de CAL-179 |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Esta tarea (CAL-121) | 2026-10-22 | 2026-10-22 | 2026-10-22 11:18 | | — |
| Ejecución de las pruebas | 2026-10-22 | 2026-10-22 | 2026-10-22 11:24 | 2026-10-22 11:44 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Cubrir con pruebas automatizadas las reglas del alta de restaurantes que no tenían prueba: el formato y la longitud del identificador, los límites de usuarios y de mesas de cada plan justo en el borde, y las validaciones del formulario de alta.

**Fuera de alcance.** Lo que ya cubrían `TenantOnboardingServiceTest`, `TenantOnboardingServiceCompensationTest`, `SignupPasswordPolicyTest`, `SignupRateLimiterTest`, `SignupRateLimitTest`, `PlanLimitServiceConcurrencyTest` y `ControlPlaneMappingTest`: el alta completa, la compensación si falla el administrador, la política de contraseñas del servidor, el límite de altas por IP y la carrera por el último cupo de usuario.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| Slug con formato inválido | C01 |
| Slug duplicado | C02 |
| Límite de usuarios de cada plan (FREE, PRO, PREMIUM) en el borde | C03 |
| Límite de mesas de cada plan en el borde | C04 |
| Validaciones del formulario de alta en el frontend | C05 |
| Criterio de salida: casos válido, inválido y de borde por regla; CI en verde | C01 a C06 |

## 5. Preparación

- [x] `SignupRequest` validado con el validador real de Jakarta Bean Validation, solo sobre la propiedad `slug`
- [x] Límites de plan con Spring y PostgreSQL 16 (Testcontainers): se da de alta un restaurante por plan y se llenan los cupos directamente en la base, dentro de la transacción del tenant (RLS activo)
- [x] `SignupView` montado con `@testing-library/react`; `api.post` y `useRouter` simulados
- [x] Docker Desktop encendido y JDK 21 en `JAVA_HOME`

## 6. Casos de prueba

### C01 — El identificador solo admite de 3 a 80 minúsculas, números y guiones

| Campo | Valor |
|---|---|
| Criterio cubierto | Slug con formato inválido |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:28 / 2026-10-22 11:29 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Válido | `abc`, `la-esquina`, `cocina-2`, `123` | Sin errores | Aprobado | `SignupRequestSlugTest.acceptsValidSlugs` |
| 2 | Inválido | `ab`, `a`, `La-Esquina`, `la_esquina`, `la esquina`, `cafe-niño`, `la.esquina`, ` abc`, `abc/admin` | «Solo minusculas, numeros y guiones, entre 3 y 80 caracteres.» | Aprobado en los 9 | `rejectsInvalidSlugs` |
| 3 | Borde de longitud | 2, 3, 80 y 81 caracteres | 3 y 80 pasan; 2 y 81 se rechazan | Aprobado | `lengthBoundsAreExact` |
| 4 | Vacío, en blanco o nulo | `""`, `"   "`, `null` | Obligatorio | Aprobado | `blankOrNullSlugIsRequired` |

### C02 — Un identificador ocupado se rechaza

| Campo | Valor |
|---|---|
| Criterio cubierto | Slug duplicado |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:39 / 2026-10-22 11:44 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Dar de alta dos restaurantes con el mismo identificador | `onboarding-dup` | El segundo se rechaza con un mensaje que nombra el identificador | Aprobado | `TenantOnboardingServiceTest.rejectsADuplicateSlug` (ya existía) |
| 2 | Alta duplicada por la API | — | `409` y el intento consume cupo del límite de altas | Aprobado | `SignupRateLimitTest.conflictsConsumeQuota` (ya existía) |
| 3 | El formulario recibe el `409` | «Ya existe un restaurante con el identificador 'la-esquina'.» | Aviso general «No se pudo completar el registro» con el detalle; no navega | Aprobado | `signup-form.test.tsx` (nueva) |

**Observaciones.** Los pasos 1 y 2 ya estaban cubiertos; se re-ejecutaron dentro de la suite completa (C06).

### C03 — Límite de usuarios de cada plan en el borde

| Campo | Valor |
|---|---|
| Criterio cubierto | Límite de usuarios de cada plan |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:28 / 2026-10-22 11:29 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Plan | Último permitido | Primer rechazado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | FREE | Usuario 3 | Usuario 4: «Alcanzaste el limite de 3 usuarios del plan FREE.» | Aprobado | `PlanLimitBoundaryTest.userLimitAtTheBoundary` [1] |
| 2 | PRO | Usuario 15 | Usuario 16 | Aprobado | [2] |
| 3 | PREMIUM | Usuario 60 | Usuario 61 | Aprobado | [3] |
| 4 | Planes sembrados | FREE 3/5, PRO 15/30, PREMIUM 60/120 (usuarios/mesas) | Iguales a los que publica `frontend/src/lib/plans.ts` | Aprobado | `seededPlansMatchThePublishedLimits` |

### C04 — Límite de mesas de cada plan en el borde

| Campo | Valor |
|---|---|
| Criterio cubierto | Límite de mesas de cada plan |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:28 / 2026-10-22 11:29 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-178](https://linear.app/callejon19/issue/CAL-178/aplicar-el-limite-de-mesas-del-plan-al-crear-una-mesa) |

| # | Plan | Último permitido | Primer rechazado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | FREE | Mesa 5: se crea | Mesa 6: `BusinessRuleException` | **La mesa 6 se crea sin error** | [`mesas-limite-no-aplicado.txt`](respuestas/mesas-limite-no-aplicado.txt) |
| 2 | PRO | Mesa 30 | Mesa 31 | **La mesa 31 se crea** | |
| 3 | PREMIUM | Mesa 120 | Mesa 121 | **La mesa 121 se crea** | |

**Observaciones.** `TableService.createTable` solo comprueba que el número no esté repetido: ningún código consulta `plans.max_tables`. La prueba `PlanLimitBoundaryTest.tableLimitAtTheBoundary` queda en el repositorio con `@Disabled("CAL-178: …")` para que CI siga en verde, y se activa con la corrección.

### C05 — Validaciones del formulario de alta

| Campo | Valor |
|---|---|
| Criterio cubierto | Validaciones del formulario de alta en el frontend |
| Prioridad | Media |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:24 / 2026-10-22 11:39 |
| Estado | ✅ Aprobado (tras corregir CAL-179) |
| Defecto vinculado | [CAL-179](https://linear.app/callejon19/issue/CAL-179/el-campo-identificador-del-alta-no-valida-el-formato-en-el-navegador), corregido y re-probado el 2026-10-22 |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Evaluar el `pattern` del identificador como el navegador (`^(?:pattern)$`, bandera `v`) | Los 9 valores de C01 más los bordes | Acepta y rechaza lo mismo que el backend | **Antes de corregir: las 9 pruebas fallan** porque `[a-z0-9-]` es inválido con la bandera `v` y el navegador descarta el patrón. Tras escapar el guion: aprobado | [`formulario-antes-de-corregir.txt`](respuestas/formulario-antes-de-corregir.txt), [`frontend.txt`](respuestas/frontend.txt) |
| 2 | Comprobar el mismo `pattern` en Edge 155 | `la-esquina`, `La_Esquina`, `ab` | `La_Esquina` y `ab` marcados como inválidos | Sin escapar: los tres válidos y error en la consola. Escapado: `La_Esquina` y `ab` inválidos | [`patron-en-edge.txt`](respuestas/patron-en-edge.txt) |
| 3 | Campos obligatorios y topes | — | Todos `required`; 160, 160, 180 y 72 caracteres; correo con `type="email"` | Aprobado | `signup-form.test.tsx` |
| 4 | Contraseña débil | Corta, sin números, con el identificador | Mensaje de la política, `aria-invalid` y sin llamar a la API | Aprobado en las 3 | |
| 5 | Alta válida | Plan «Gratis» elegido | `POST /api/v1/signup` con `planCode: "FREE"` y navegación a `/login?slug=la-esquina` | Aprobado | |
| 6 | Alta sin elegir plan | — | Se envía `PRO`, el preseleccionado | Aprobado | |
| 7 | `400` con error por campo | `errors.slug` | El mensaje aparece debajo del identificador, sin aviso general | Aprobado | |

**Observaciones.** La corrección de CAL-179 es un carácter en `signup-view.tsx`: `pattern="[a-z0-9\-]{3,80}"`. El servidor siempre rechazó esos identificadores (`400`), así que el defecto solo quitaba el aviso inmediato del navegador.

### C06 — Puertas completas y mutaciones

| Campo | Valor |
|---|---|
| Criterio cubierto | Criterio de salida |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 11:34 / 2026-10-22 11:44 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Quince mutaciones del código de producción, una a la vez | 5 en `SignupRequest`, 2 en `PlanLimitService`, 8 en `signup-view.tsx` | Cada mutación hace fallar al menos una prueba | **15 de 15 detectadas** | [`mutaciones.txt`](respuestas/mutaciones.txt) |
| 2 | `.\mvnw.cmd -B verify` | JDK 21 y PostgreSQL 16 con Testcontainers | Todas las pruebas en verde; la de mesas, omitida por CAL-178 | **646 pruebas, 0 fallas, 1 omitida** (la de mesas); SpotBugs sin hallazgos y `BUILD SUCCESS` | [`backend-verify.txt`](respuestas/backend-verify.txt) |
| 3 | `pnpm test`, `pnpm lint`, `pnpm exec tsc --noEmit` | — | Sin fallas | **169 de 169**; lint y tipos sin errores | [`frontend.txt`](respuestas/frontend.txt) |

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-178](https://linear.app/callejon19/issue/CAL-178/aplicar-el-limite-de-mesas-del-plan-al-crear-una-mesa) | C04 | Media | El límite de mesas del plan no se aplica: FREE crea la mesa 6, PRO la 31 y PREMIUM la 121 | Abierto | — |
| [CAL-179](https://linear.app/callejon19/issue/CAL-179/el-campo-identificador-del-alta-no-valida-el-formato-en-el-navegador) | C05 | Baja | El `pattern` del identificador es inválido con la bandera `v` y el navegador no valida el formato | Corregido en este PR | 2026-10-22 |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 6 | 5 | 1 | 0 | 0 |

**Desviaciones y limitaciones.**

- La prueba de mesas (C04) se ejecutó activada a las 11:28 para registrar la falla, y después se dejó con `@Disabled` y el ID de CAL-178. En la suite completa aparece como omitida.
- El paso 1 de C05 se ejecutó antes y después de corregir CAL-179; el código de producción de este PR incluye esa corrección.
- La comprobación en Edge usó una página mínima con el mismo `pattern`, no la aplicación desplegada: el frontend publicado no es parte de una UT.
- Las pruebas se ejecutaron con los cambios todavía sin commit; el commit y los checks del PR se anotan al abrirlo.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es la misma de las SIT, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** Cuatro de los cinco criterios de CAL-121 quedan cubiertos con casos válidos, inválidos y de borde que fallan sin el comportamiento que protegen: el identificador solo admite de 3 a 80 minúsculas, números y guiones; uno ocupado se rechaza; cada plan admite justo su último usuario y rechaza el siguiente; y el formulario de alta valida campos, contraseña y respuestas del servidor. El quinto, el límite de mesas, **no se cumple**: ningún plan limita sus mesas, y queda registrado como CAL-178 con la prueba lista para activarse. Además, la prueba del formulario descubrió que el navegador ignoraba el formato del identificador (CAL-179); se corrigió en este PR y se re-probó. Las 15 mutaciones se detectan y la suite completa queda en verde: 646 pruebas del backend, con la de mesas omitida, y 169 del frontend.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/.../platform/tenant/web/dto/SignupRequestSlugTest.java` (15 casos), `backend/.../platform/PlanLimitBoundaryTest.java` (9 casos: 3 de planes sembrados, 3 de usuarios y 3 de mesas omitidos por CAL-178), `frontend/src/test/signup-form.test.tsx` (17) |
| Comando local | `cd backend; .\mvnw.cmd -B verify` y `cd frontend; pnpm test; pnpm lint; pnpm exec tsc --noEmit` |
| Resultado local | Backend: 646 pruebas en verde, 1 omitida por CAL-178, y SpotBugs sin hallazgos. Frontend: 169 pruebas, lint y tipos en verde |
| Resultado en CI | Checks del PR |
| Prueba de que fallan sin el comportamiento | Quince mutaciones: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Capa | Pruebas que fallan |
|---|---|---|
| B1 · el identificador admite 2 caracteres | Backend | 2 de 15 |
| B2 · el identificador admite 81 caracteres | Backend | 1 de 15 |
| B3 · el identificador admite mayúsculas | Backend | 1 de 15 |
| B4 · el identificador admite guion bajo y punto | Backend | 2 de 15 |
| B5 · el identificador deja de ser obligatorio | Backend | 1 de 15 |
| B6 · el plan admite un usuario de más (`>` en vez de `>=`) | Backend | 3 de 6 |
| B7 · el plan rechaza el último cupo permitido | Backend | 3 de 6 |
| F1 · el `pattern` vuelve a la versión sin escapar (CAL-179) | Frontend | 9 de 17 |
| F2 · el `pattern` admite 81 caracteres | Frontend | 1 de 17 |
| F3 · el identificador deja de ser obligatorio | Frontend | 1 de 17 |
| F4 · el nombre del restaurante admite 161 caracteres | Frontend | 1 de 17 |
| F5 · no se valida la contraseña antes de enviar | Frontend | 3 de 17 |
| F6 · el plan preseleccionado pasa a FREE | Frontend | 1 de 17 |
| F7 · el aviso general aparece aunque haya errores por campo | Frontend | 1 de 17 |
| F8 · tras el alta no se lleva el identificador al login | Frontend | 1 de 17 |

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las pruebas fallan sin el comportamiento que protegen (sección 9)
- [ ] Los checks del PR pasan, incluido SpotBugs con Java 21
- [ ] Los defectos están registrados en Linear y enlazados
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las salidas no fueron editadas salvo para ocultar secretos, datos personales y rutas locales, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Braulio Hernández Mejía · **Fecha:** 2026-10-22
