# Evidencia de prueba · CAL-121 — UT · Alta de restaurantes y planes

## 1. Identificación

| Campo | Valor |
|---|---|
| Historia padre | CAL-119 — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Issue | CAL-121 — UT · Alta de restaurantes y planes |
| Tipo / módulo | UT / `platform`, `/signup`, `/platform` y `table` |
| Rama | `test/cal-121-plan-signup-ut` |
| Entorno | Worktree local; PostgreSQL 16.15 en Testcontainers para `mvnw verify`; JSDOM para la prueba React |

## 2. Responsables

| Rol | Responsable |
|---|---|
| Implementación y ejecución | Sesión Codex |
| Revisión independiente | Pendiente; esta evidencia no declara una autorrevisión |

## 3. Fechas

Fechas en `America/Mexico_City` (UTC-06:00). Linear no tiene fechas planeadas ni fecha límite para CAL-121. La ejecución local registrada fue el `2026-10-22`, entre `11:40` y `11:59`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real |
|---|---|---|---|---|
| CAL-119 | No definido en Linear | No definido en Linear | — | — |
| CAL-121 | No definido en Linear | No definido en Linear | `2026-10-22 11:40` | `2026-10-22 11:59` |
| UT backend focales | — | — | `2026-10-22 11:53` | `2026-10-22 11:53` |
| `mvnw verify` | — | — | `2026-10-22 11:53` | `2026-10-22 11:56` |
| UT frontend, lint y tipos | — | — | `2026-10-22 11:58` | `2026-10-22 11:59` |

No se modificó el estado de CAL-121 en Linear; al consultar el issue seguía en `Todo`.

## 4. Alcance y criterios cubiertos

**Objetivo.** Verificar validaciones de slug, límites de usuarios y mesas en el borde de cada plan y validaciones del formulario de alta.

**Fuera de alcance.** SIT, despliegue, cobertura JaCoCo y validación visual en un navegador real.

| Criterio del issue | Caso(s) |
|---|---|
| Slug duplicado o con formato inválido | C01 |
| Límites de usuarios de FREE, PRO y PREMIUM: último permitido y primero rechazado | C02 |
| Límites de mesas de FREE, PRO y PREMIUM: última permitida y primera rechazada | C03 |
| Validaciones del formulario de alta | C04 |
| Casos válidos, inválidos y de borde; suite en CI | C01–C04; consultar los checks del PR |

## 5. Preparación

- Backend: Java 26.0.2 disponible localmente; Docker Desktop 29.8.2; Testcontainers creó PostgreSQL 16.15.
- Frontend: dependencias instaladas desde `pnpm-lock.yaml`; prueba React ejecutada con JSDOM.
- No se usaron credenciales, datos personales ni secretos.
- Datos de plan: FREE (`maxUsers=3`, `maxTables=5`), PRO (`15`, `30`) y PREMIUM (`60`, `120`).

## 6. Casos de prueba

### C01 — El alta acepta slugs válidos y rechaza formato inválido o repetido

| Campo | Valor |
|---|---|
| Prioridad | Alta |
| Estado | ✅ Aprobado en ejecución local |
| Defecto vinculado | — |

**Pasos y resultado.** `SignupRequestValidationTest` rechaza slugs cortos, mayúsculas/espacios, guion bajo, puntuación y más de 80 caracteres; acepta slugs en minúsculas, números y guiones. `TenantOnboardingServiceTest.rejectsADuplicateSlug` ya verifica el duplicado en el servicio de alta.

### C02 — Cada plan permite el último usuario y rechaza el siguiente

| Campo | Valor |
|---|---|
| Prioridad | Alta |
| Estado | ✅ Aprobado en ejecución local |
| Defecto vinculado | — |

**Pasos y resultado.** `PlanLimitServicePlanBoundaryTest` ejecuta ambos bordes para FREE, PRO y PREMIUM: permite el último cupo, rechaza cuando el conteo ya llegó al máximo y verifica el bloqueo de la fila del restaurante.

### C03 — Cada plan permite la última mesa y rechaza la siguiente

| Campo | Valor |
|---|---|
| Prioridad | Alta |
| Estado | ✅ Aprobado en ejecución local |
| Defecto vinculado | — |

**Pasos y resultado.** `PlanLimitServicePlanBoundaryTest` ejecuta los dos bordes para los tres planes. `TableServiceTest` confirma que alta y reactivación consultan el límite y no guardan una mesa cuando el plan está lleno.

**Corrección incorporada.** El servicio de mesas no aplicaba `maxTables`. `PlanLimitService.assertCanAddTable` ahora serializa la operación bloqueando la fila del restaurante, consulta las mesas activas bajo RLS y rechaza el alta o reactivación cuando se alcanzó el máximo.

### C04 — El formulario valida campos, correo y slug

| Campo | Valor |
|---|---|
| Prioridad | Media |
| Estado | ✅ Aprobado en JSDOM |
| Defecto vinculado | — |

**Pasos y resultado.** `signup-form.test.tsx` comprueba campos requeridos, longitud máxima, tipo de correo, slugs cortos, largos y con caracteres no permitidos, y un slug/correo válidos.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción | Estado |
|---|---|---|---|---|
| — | C03 | Media | El alta y la reactivación de mesas no aplicaban el límite del plan. | Corregido en esta rama |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---:|---:|---:|---:|---:|
| 4 casos | 4 | 0 | 0 | 0 |

**Puertas ejecutadas.**

- Backend focal: 38 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Backend completo: 645 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Frontend `pnpm test`: 153 pruebas en 23 archivos, todo aprobado.
- Frontend `pnpm lint`: aprobado.
- Frontend `pnpm exec tsc --noEmit`: aprobado.

**Limitación local.** `mvnw verify` terminó en SpotBugs con `Unsupported class file major version 70`: el equipo local ejecuta Java 26.0.2 y el plugin SpotBugs 4.8.6.6 no puede analizarlo. Las 645 pruebas terminaron correctamente antes de ese paso. El workflow de CI usa Temurin 21; consulta en el PR el resultado de esa puerta.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Archivos de prueba | `PlanLimitServicePlanBoundaryTest.java`, `SignupRequestValidationTest.java`, `TableServiceTest.java`, `signup-form.test.tsx` |
| Backend focal | `cd backend; .\mvnw.cmd -B "-Dtest=PlanLimitServicePlanBoundaryTest,SignupRequestValidationTest,TableServiceTest" test` — 38/38 aprobadas |
| Backend completo | `cd backend; .\mvnw.cmd -B verify` — 645/645 pruebas aprobadas; la fase SpotBugs requiere Java 21 |
| Frontend | `cd frontend; pnpm test` — 153/153; `pnpm lint` y `pnpm exec tsc --noEmit` aprobados |
| Mutación temporal | No ejecutada en esta rama; la evidencia de una regla rota para CAL-119 debe consultarse en la entrega CAL-125 |
| CI | Resultado remoto disponible en los checks del PR |

## 10. Revisión

- [x] Todos los criterios de CAL-121 están mapeados a casos.
- [x] No se incluyeron contraseñas, tokens, cookies ni datos personales.
- [ ] Revisión independiente pendiente.
- [ ] Checks del PR aprobados.

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| Pendiente | — | — | No se declara autorrevisión. |

## 11. Declaración de autenticidad

Esta evidencia resume comandos ejecutados localmente el `2026-10-22`. Las cifras de pruebas proceden de sus salidas reales. No se adjuntan capturas porque los casos son UT automatizadas; el entorno JSDOM no representa una sesión de navegador real.

**Ejecutor:** sesión Codex · **Fecha:** `2026-10-22`
