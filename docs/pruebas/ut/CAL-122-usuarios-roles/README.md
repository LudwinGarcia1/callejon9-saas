# Evidencia de prueba · CAL-122 — UT · Usuarios y roles

> **v1.** Ejecución completa con el alcance ajustado del 2026-10-22 (sección 4); pendiente de revisión por otro integrante (sección 10). Defecto registrado en Linear: CAL-180 (C06).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-122](https://linear.app/callejon19/issue/CAL-122/ut-usuarios-y-roles) — UT · Usuarios y roles |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | Usuarios y roles (`user/service/UserService`, `user/web/dto/CreateUserRequest`, `auth/password/PasswordPolicy`, `platform/tenant/service/PlanLimitService`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia; pruebas ejecutadas en la rama `test/cal-122-ut-usuarios-roles` desde `main` `d31fd0c`, con los cambios aún sin commit (el código de producción no cambia) |
| Ejecución de CI | Checks del PR |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Herramientas | JDK 21.0.9 (como CI), Maven Wrapper, JUnit 5, Spring MockMvc y Testcontainers con PostgreSQL 16 en Docker 29.8.2 |
| Versión de esta evidencia | v1 · 2026-10-22 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Ajuste del alcance, pruebas, mutaciones y registro de CAL-180 |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Esta tarea (CAL-122) | 2026-10-22 | 2026-10-22 | 2026-10-22 12:58 | | Se ajustó el alcance en Linear a las 13:02 (sección 4) |
| Ejecución de las pruebas | 2026-10-22 | 2026-10-22 | 2026-10-22 13:03 | 2026-10-22 13:23 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Cubrir con pruebas automatizadas las reglas del módulo de usuarios que no tenían prueba en sus bordes: el rol con que queda la sesión, los roles que no se pueden asignar, la contraseña al crear al usuario, el cupo del plan con usuarios desactivados y las validaciones del correo y el nombre.

**Alcance ajustado.** Dos de los tres puntos originales del issue prueban funciones que no existen en `main` `d31fd0c`: `PATCH /api/v1/users/{id}` solo acepta `{ "active": … }` (`UpdateUserStatusRequest`), así que no hay cambio de rol, y no hay edición de contraseña. El rol y la contraseña solo se fijan al crear al usuario. Construir esas funciones queda fuera de una tarea de pruebas, así que el alcance se ajustó en [CAL-122](https://linear.app/callejon19/issue/CAL-122/ut-usuarios-y-roles) el 2026-10-22 a las 13:02:

| Punto original | Alcance ajustado | Caso |
|---|---|---|
| Cambio de rol y su efecto en la siguiente sesión | El rol asignado al crear es el de la sesión; tras desactivar y reactivar, la sesión vieja sigue revocada y la nueva conserva el rol. Los roles no asignables se rechazan | C01, C02 |
| Contraseña débil o con datos personales al editar | La misma regla al crear al usuario, el único momento en que se fija | C03 |
| Alta que rebasa el límite de usuarios del plan | Ya cubierto (`UserControllerTest`, `PlanLimitServiceConcurrencyTest`; PRO y PREMIUM en CAL-121). Se agrega el borde de los usuarios desactivados | C04 |
| — (agregado) | Validaciones del alta: correo, nombre y correo duplicado aunque cambien las mayúsculas | C05, C06 |
| Criterio de salida: casos válido, inválido y de borde por regla; CI en verde | | C01 a C07 |

**Fuera de alcance.** Lo que ya cubren `UserControllerTest`, `UserServiceConcurrencyTest`, `UserTenantAssignmentTest` y `DeactivationSessionRaceTest`: el alta y el listado, la autodesactivación, el último administrador, la revocación de sesiones al desactivar, el `403` para roles distintos de `ADMIN` y el aislamiento entre restaurantes.

## 5. Preparación

- [x] Por cada prueba, un restaurante `usuarios-borde` con plan FREE (3 usuarios) dado de alta con el servicio real y borrado al terminar
- [x] Las peticiones pasan por la API real con MockMvc y la cookie de sesión del administrador; el login también es el real
- [x] Docker Desktop encendido y JDK 21 en `JAVA_HOME`

## 6. Casos de prueba

### C01 — El rol de la sesión es el que se asignó al crear al usuario

| Campo | Valor |
|---|---|
| Criterio cubierto | El rol y la sesión |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN (crea) y el rol creado (inicia sesión) |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Crear un usuario con cada rol y que inicie sesión | ADMIN, WAITER, KITCHEN, CASHIER | `/auth/login` y `/auth/me` devuelven el mismo rol | Aprobado en los 4 | `sessionCarriesTheRoleAssignedAtCreation` |
| 2 | Desactivar y reactivar a un cajero con sesión abierta | CASHIER | La sesión vieja da `401`; un login nuevo entra con rol CASHIER | Aprobado | `reactivatedUserNeedsANewSessionWithTheSameRole` |

### C02 — Los roles que no se pueden asignar se rechazan

| Campo | Valor |
|---|---|
| Criterio cubierto | El rol y la sesión |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Crear con un rol no asignable | `SUPER_ADMIN`, `GERENTE`, `waiter`, `""` | `400`, y el usuario no puede iniciar sesión porque no existe | Aprobado en los 4 | `invalidRolesAreRejected` |
| 2 | Crear sin rol | `"role": null` | `400` con `errors.role` | Aprobado | `missingRoleIsRejected` |

### C03 — La contraseña al crear al usuario cumple la política

| Campo | Valor |
|---|---|
| Criterio cubierto | Contraseña débil o con datos personales |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Borde: 10 caracteres | `Azul-47890` | `201` | Aprobado | `tenCharactersAreAccepted` |
| 2 | Borde: 9 caracteres | `Azul-4789` | `400` «La contrasena debe tener al menos 10 caracteres.» | Aprobado | `weakPasswordsAreRejected` |
| 3 | Sin números, repetitiva o común | `Mantel-Azul-Rojo`, `aaaaaaaaa1`, `qwerty123456` | `400` con el mensaje de cada regla en `errors.password` | Aprobado en las 3 | |
| 4 | Con datos personales del usuario «Rosa Delgado», `rmontes@…` | `Delgado-2026x`, `rmontes-2026`, `ROSA-azul-47` | `400` «La contrasena no debe incluir tu nombre, tu correo…», sin importar mayúsculas | Aprobado en las 3 | `passwordsWithPersonalDataAreRejected` |

### C04 — Los usuarios desactivados siguen ocupando cupo del plan

| Campo | Valor |
|---|---|
| Criterio cubierto | Alta que rebasa el límite de usuarios del plan |
| Prioridad | Media |
| Rol que ejecuta | ADMIN |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Llenar el plan FREE y desactivar al tercer usuario | Administrador + 2 usuarios | El tercero queda desactivado | Aprobado | `inactiveUsersKeepTheirSlot` |
| 2 | Crear un cuarto usuario | — | `409` «Alcanzaste el limite de 3 usuarios del plan FREE.» | Aprobado | |
| 3 | Reactivar al tercero | — | `200` y puede iniciar sesión: reactivar no rebasa el tope | Aprobado | |

**Observaciones.** Es el comportamiento implementado: `PlanLimitService` cuenta todos los usuarios del restaurante, activos o no, y la desactivación es lógica. Queda como hallazgo para que producto confirme si desactivar debería liberar el cupo (sección 7).

### C05 — Las validaciones del correo y el nombre respetan sus topes

| Campo | Valor |
|---|---|
| Criterio cubierto | Validaciones del alta |
| Prioridad | Media |
| Rol que ejecuta | ADMIN |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Borde superior | Correo de 180 caracteres; nombre de 160 | `201` | Aprobado | `maximumLengthsAreAccepted` |
| 2 | Un carácter más | Correo de 181; nombre de 161 | `400` en `errors.email` y `errors.fullName` | Aprobado | `invalidFieldsAreRejected` |
| 3 | Formato y vacío | `no-es-correo`; nombre `" "` | `400` en su campo | Aprobado | |

### C06 — Un correo que solo cambia en mayúsculas es el mismo usuario

| Campo | Valor |
|---|---|
| Criterio cubierto | Validaciones del alta |
| Prioridad | Alta |
| Rol que ejecuta | ADMIN; el usuario creado inicia sesión |
| Inicio / fin de ejecución | 2026-10-22 13:06 / 2026-10-22 13:07 |
| Estado | ❌ Fallido |
| Defecto vinculado | [CAL-180](https://linear.app/callejon19/issue/CAL-180/el-correo-de-los-usuarios-distingue-mayusculas-y-bloquea-el-login) |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Crear `mesero@usuarios-borde.com` y después `Mesero@Usuarios-Borde.com` | — | El segundo da `409` por duplicado | **`201`: quedan dos usuarios** | [`correo-mayusculas.txt`](respuestas/correo-mayusculas.txt) |
| 2 | Crear `Ana.Caja@Usuarios-Borde.com` e iniciar sesión con `ana.caja@usuarios-borde.com`, como lo envía la app móvil | — | `200` | **`401`** | |

**Observaciones.** El backend compara el correo exacto (`findByEmail`) y la base solo exige `UNIQUE (tenant_id, email)`. La app móvil convierte el correo a minúsculas antes de iniciar sesión (`mobile/src/screens/LoginScreen.tsx`, línea 52), así que un usuario creado con alguna mayúscula nunca puede entrar desde el teléfono. Las dos pruebas quedan con `@Disabled("CAL-180: …")` para que CI siga en verde, y se activan con la corrección.

### C07 — Puertas completas y mutaciones

| Campo | Valor |
|---|---|
| Criterio cubierto | Criterio de salida |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 13:13 / 2026-10-22 13:23 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Diez mutaciones del código de producción, una a la vez | `UserService`, `CreateUserRequest`, `PasswordPolicy`, `PlanLimitService` | Cada mutación hace fallar al menos una prueba | **10 de 10 detectadas** | [`mutaciones.txt`](respuestas/mutaciones.txt) |
| 2 | `.\mvnw.cmd -B verify` | JDK 21 y PostgreSQL 16 con Testcontainers | Todas las pruebas en verde; las dos de CAL-180, omitidas | **647 pruebas, 0 fallas, 2 omitidas** (las de CAL-180); SpotBugs sin hallazgos y `BUILD SUCCESS` | [`backend-verify.txt`](respuestas/backend-verify.txt) |

**Observaciones.** El issue solo lleva la etiqueta Backend y el código del frontend no cambia, así que no se corrieron las puertas del frontend.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| [CAL-180](https://linear.app/callejon19/issue/CAL-180/el-correo-de-los-usuarios-distingue-mayusculas-y-bloquea-el-login) | C06 | Media | El correo distingue mayúsculas: se duplican usuarios y uno creado con mayúsculas no entra desde la app móvil | Abierto | — |

**Hallazgo para producto (no es defecto).** Un usuario desactivado sigue ocupando cupo del plan (C04). Es coherente con la desactivación lógica, pero el plan dice «hasta 3 usuarios» sin aclarar si cuentan los desactivados. Conviene confirmarlo y, si se decide liberar el cupo, la prueba de C04 cambia junto con `PlanLimitService`.

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 7 | 6 | 1 | 0 | 0 |

**Desviaciones y limitaciones.**

- El alcance se ajustó a las 13:02, antes de ejecutar, porque dos puntos del issue prueban funciones que no existen (sección 4).
- Las dos pruebas de C06 se ejecutaron activadas a las 13:06 para registrar la falla, y después se dejaron con `@Disabled` y el ID de CAL-180. En la suite completa aparecen como omitidas.
- Una primera corrida, de 13:03 a 13:06, tuvo una falla más, propia de la prueba: el caso ADMIN de C01 usaba el mismo correo que el administrador del alta y recibió `409`. Se corrigió la prueba y la corrida de las 13:06 es la que se registra.
- Las pruebas se ejecutaron con los cambios todavía sin commit; el commit y los checks del PR se anotan al abrirlo.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es la misma de las SIT, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** Con el alcance ajustado, las reglas del módulo de usuarios quedan cubiertas con casos válidos, inválidos y de borde que fallan sin el comportamiento que protegen: el rol asignado al crear es el de la sesión y sobrevive a una desactivación y reactivación con un login nuevo; SUPER_ADMIN, un rol inexistente o la falta de rol se rechazan; la contraseña al crear cumple la política en sus bordes, incluidos los datos personales; los usuarios desactivados siguen ocupando cupo; y el correo y el nombre respetan sus topes. El correo, en cambio, **distingue mayúsculas** (CAL-180): se pueden duplicar usuarios y uno creado con mayúsculas no puede entrar desde la app móvil. Las 10 mutaciones se detectan y la suite completa queda en verde: 647 pruebas, con las 2 de CAL-180 omitidas.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/.../user/UserRulesBoundaryTest.java` (23 casos: 21 activos y 2 omitidos por CAL-180) |
| Comando local | `cd backend; .\mvnw.cmd -B verify` |
| Resultado local | 647 pruebas en verde, 2 omitidas por CAL-180, y SpotBugs sin hallazgos |
| Resultado en CI | Checks del PR |
| Prueba de que fallan sin el comportamiento | Diez mutaciones: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Archivo | Pruebas que fallan |
|---|---|---|
| M1 · SUPER_ADMIN se puede asignar desde un restaurante | `UserService` | 1 de 21 |
| M2 · el rol deja de ser obligatorio | `CreateUserRequest` | 1 de 21 |
| M3 · el correo admite 181 caracteres | `CreateUserRequest` | 1 de 21 |
| M4 · el nombre admite 161 caracteres | `CreateUserRequest` | 1 de 21 |
| M5 · la contraseña ya no se compara con el nombre | `CreateUserRequest` | 2 de 21 |
| M6 · la contraseña ya no se compara con el correo | `CreateUserRequest` | 1 de 21 |
| M7 · el mínimo de la contraseña baja a 9 caracteres | `PasswordPolicy` | 1 de 21 |
| M8 · el mínimo de la contraseña sube a 11 caracteres | `PasswordPolicy` | 3 de 21 |
| M9 · los usuarios desactivados dejan de ocupar cupo | `PlanLimitService` | 1 de 21 |
| M10 · desactivar ya no revoca las sesiones | `UserService` | 1 de 21 |

## 10. Revisión

- [ ] Cada criterio del issue (con el alcance ajustado) tiene al menos un caso con resultado
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

## Evidencia complementaria de la rama integrada

Se conserva la evidencia previa de esta rama en [evidencia-rama-original.md](evidencia-rama-original.md). Es una ejecucion distinta de la descrita arriba; no se reemplazan sus responsables, resultados ni defectos. La integracion conserva las pruebas de ambas ramas.

La rama incorpora ademas la edicion de rol y contrasena por API y la revocacion de sesiones correspondiente. Sus pruebas focalizadas y limitaciones originales se detallan en la evidencia complementaria. Esta integracion requiere una nueva revision independiente; no declara aprobacion.
