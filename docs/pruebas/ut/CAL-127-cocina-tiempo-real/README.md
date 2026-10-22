# Evidencia de prueba · CAL-127 — UT · Cocina y tiempo real

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10). Las pruebas de la primera entrega (PR #61) se conservan y se agregan los casos de borde e inválidos que les faltaban.

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-127](https://linear.app/callejon19/issue/CAL-127/ut-cocina-y-tiempo-real) — UT · Cocina y tiempo real |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | Cocina y tiempo real (`kitchen/service/KitchenService`, `frontend/src/lib/kitchen-timing.ts`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia; pruebas ejecutadas en la rama `test/cal-127-ut-cocina-tiempo-real` desde `main` `d31fd0c`, con los cambios de prueba aún sin commit (el código de producción no cambia) |
| Primera entrega | PR #61, commit `0b8887b` (2026-10-20 21:30): `KitchenServiceTest` con 7 pruebas y `kitchen-timing.test.ts` con 8 |
| Ejecución de CI | Checks del PR |
| Equipo de trabajo | Laptop Lenovo IdeaPad 3 15IIL05 (modelo 81WE) con Windows 11 Home 25H2, compilación 26200.9711, 64 bits; Intel Core i5-1035G1 a 1.00 GHz, 12 GB de RAM (11.7 GB utilizables) e Intel UHD Graphics ([`capturas/entorno_trabajo_laptop.png`](capturas/entorno_trabajo_laptop.png), 2026-10-21 22:41) |
| Herramientas | Suite completa del backend con JDK 21.0.9 (como CI), Maven Wrapper, JUnit 5, Mockito y Testcontainers con PostgreSQL 16 en Docker 29.8.2; pruebas de la cocina y mutaciones con JDK 25.0.2 (`--release 21`); Node 22.19.0 con Vitest 3.2.7 y jsdom |
| Versión de esta evidencia | v1 · 2026-10-22 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Braulio Hernández Mejía | Asignado en Linear |
| Ejecutor de la prueba | Braulio Hernández Mejía | Pruebas nuevas, mutaciones y puertas de esta evidencia. La primera entrega (PR #61) la subió Ludwin García |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Primera entrega (PR #61) | | | | 2026-10-20 21:30 | El issue pasó a Done el 2026-10-20 21:30 sin evidencia en `docs/pruebas/ut/` |
| Esta evidencia (CAL-127) | 2026-10-22 | 2026-10-22 | 2026-10-22 10:47 | | — |
| Ejecución de las pruebas | 2026-10-22 | 2026-10-22 | 2026-10-22 10:48 | 2026-10-22 11:04 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Demostrar con pruebas automatizadas que el tablero de cocina solo avanza un producto al siguiente estado, que la orden queda lista únicamente cuando todos sus productos lo están y que el semáforo y las etiquetas de tiempo del tablero cambian exactamente en sus umbrales.

**Fuera de alcance.** El canal en tiempo real (`/ws`) y la publicación de eventos, que ya cubren `KitchenTopicPublishTest`, `TenantSubscriptionInterceptorTest`, `ClientSendInterceptorTest`, `WebSocketOriginTest` y las pruebas de `RevokedSessionSweeper`, y que se probaron integrados en Azure en la SIT CAL-140.

**Revisión de la primera entrega.** Las 7 pruebas de `KitchenServiceTest` y las 8 de `kitchen-timing.test.ts` cubrían los casos válidos, un salto y un retroceso, pero dejaban sin probar el mismo estado, la salida de `DELIVERED`, la orden inexistente, la orden con un producto pendiente y los bordes exactos del tiempo. Siete de las quince mutaciones de la sección 9 pasaban sin que fallara ninguna de esas pruebas.

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| `KitchenService`: transiciones PENDING → IN_PREPARATION → READY → DELIVERED válidas | C01 |
| `KitchenService`: saltos o retrocesos rechazados | C02 |
| La orden queda lista solo cuando todos sus productos lo están | C03 |
| Frontend `lib/kitchen-timing.ts` (tiempos de espera del tablero) | C04 |
| Criterio de salida: casos válido, inválido y de borde por regla; CI en verde | C01 a C05 |

## 5. Preparación

- [x] `KitchenService` con `OrderRepository`, `OrderItemRepository` y `ApplicationEventPublisher` simulados (Mockito, con *strict stubs*); el tenant se fija con `TenantContext`
- [x] `kitchen-timing.ts` recibe la hora como parámetro, así que las pruebas usan una hora fija (`BASE_TIME`) y no dependen del reloj
- [x] Docker Desktop encendido para la suite completa del backend (Testcontainers)

## 6. Casos de prueba

### C01 — Cada producto avanza un paso a la vez

| Campo | Valor |
|---|---|
| Criterio cubierto | Transiciones válidas |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 10:48 / 2026-10-22 10:49 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Avanzar un producto pendiente | PENDING → IN_PREPARATION | Se guarda el producto, se publica el evento y la orden sigue en SENT | Aprobado | `pendingToInPreparationSuccess` |
| 2 | Avanzar el último producto en preparación | IN_PREPARATION → READY, el otro ya READY | El producto queda READY y la orden pasa a READY | Aprobado | `inPreparationToReadyPromotesOrderToReady` |
| 3 | Entregar un producto listo | READY → DELIVERED | Se guarda el producto | Aprobado | `readyToDeliveredSuccess` |
| 4 | Listar las comandas de cocina | Una orden SENT con un producto | Devuelve la orden con sus productos | Aprobado | `returnsSentOrdersWithItems` |

### C02 — Saltos, retrocesos y el mismo estado se rechazan sin tocar nada

| Campo | Valor |
|---|---|
| Criterio cubierto | Saltos o retrocesos rechazados |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 10:48 / 2026-10-22 10:49 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Saltar la preparación | PENDING → READY | 409 de negocio «No se puede mover el producto de PENDING a READY.» | Aprobado | `throwsBusinessRuleOnSkippedTransition` |
| 2 | Regresar un producto listo | READY → PENDING | 409 de negocio | Aprobado | `throwsBusinessRuleOnBackwardsTransition` |
| 3 | Las 13 combinaciones que no son el siguiente paso: saltos, retrocesos, el mismo estado y toda salida de DELIVERED | Matriz completa de 4 × 4 menos las 3 válidas | Las 13 se rechazan; el producto conserva su estado, no se guarda y no se publica evento | Aprobado en las 13 | `everyNonAdjacentTransitionIsRejected` |
| 4 | Avanzar un producto que no existe | UUID desconocido | 404 «no existe» | Aprobado | `throwsNotFoundWhenItemDoesNotExist` |
| 5 | Avanzar un producto cuya orden no existe | Orden no encontrada | 404 «La orden … no existe»; el producto no cambia | Aprobado | `throwsNotFoundWhenOrderDoesNotExist` |

**Orden de la matriz del paso 3** (el reporte de JUnit numera cada combinación): [1] PENDING → PENDING, [2] PENDING → READY, [3] PENDING → DELIVERED, [4] IN_PREPARATION → PENDING, [5] IN_PREPARATION → IN_PREPARATION, [6] IN_PREPARATION → DELIVERED, [7] READY → PENDING, [8] READY → IN_PREPARATION, [9] READY → READY, [10] DELIVERED → PENDING, [11] DELIVERED → IN_PREPARATION, [12] DELIVERED → READY, [13] DELIVERED → DELIVERED.

### C03 — La orden queda lista solo cuando todos sus productos lo están

| Campo | Valor |
|---|---|
| Criterio cubierto | Orden lista solo con todos sus productos listos |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 10:48 / 2026-10-22 10:49 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Válido: el último producto pendiente queda listo | Dos productos, el otro ya READY | La orden pasa de SENT a READY y se guarda | Aprobado | `inPreparationToReadyPromotesOrderToReady` |
| 2 | Inválido: un producto queda listo y el otro sigue en preparación | READY + IN_PREPARATION | La orden sigue en SENT y no se guarda | Aprobado | `orderStaysSentWhileAnotherItemIsInPreparation` |
| 3 | Borde: el otro producto ya se entregó | READY + DELIVERED | DELIVERED cuenta como listo: la orden pasa a READY | Aprobado | `deliveredItemCountsAsReady` |
| 4 | Borde: se entrega el último producto de una orden que ya está READY | READY → DELIVERED | La orden no se vuelve a guardar | Aprobado | `orderAlreadyReadyIsNotSavedAgain` |

### C04 — El semáforo y la etiqueta de espera cambian justo en sus umbrales

| Campo | Valor |
|---|---|
| Criterio cubierto | `lib/kitchen-timing.ts` |
| Prioridad | Media |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 10:48 / 2026-10-22 10:49 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Regla | Último valor con el resultado anterior | Primer valor con el resultado nuevo | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Semáforo «normal» → «warning» | 14 min 59 s: normal | 15 min: warning | Aprobado | [`frontend.txt`](respuestas/frontend.txt) |
| 2 | Semáforo «warning» → «critical» | 24 min 59 s: warning | 25 min: critical | Aprobado | |
| 3 | Fecha de envío nula o en el futuro (reloj adelantado) | — | normal y «recién enviada», sin minutos negativos | Aprobado | |
| 4 | Etiqueta «recién enviada» → minutos | 59 s: «recién enviada» | 60 s: «hace 1 min» | Aprobado | |
| 5 | Etiqueta minutos → horas | 59 min: «hace 59 min»; 119 min: «hace 1 h 59 min» | 60 min: «hace 1 h»; 120 min: «hace 2 h» | Aprobado | |

### C05 — Puertas completas y mutaciones

| Campo | Valor |
|---|---|
| Criterio cubierto | Criterio de salida |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-22 10:50 / 2026-10-22 11:04 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Quince mutaciones del código de producción, una a la vez | 9 en `KitchenService`, 6 en `kitchen-timing.ts` | Cada mutación hace fallar al menos una prueba | **15 de 15 detectadas** | [`mutaciones.txt`](respuestas/mutaciones.txt) |
| 2 | `.\mvnw.cmd -B verify` | JDK 21 y PostgreSQL 16 con Testcontainers | Todas las pruebas en verde | **641 de 641** con JDK 21; SpotBugs sin hallazgos y `BUILD SUCCESS` | [`backend-verify.txt`](respuestas/backend-verify.txt) |
| 3 | `pnpm test`, `pnpm lint`, `pnpm exec tsc --noEmit` | — | Sin fallas | **157 de 157**; lint y tipos sin errores | [`frontend.txt`](respuestas/frontend.txt) |

**Observaciones.** En la línea de `KitchenServiceTest`, Maven imprime «Tests run: 0» porque todas sus pruebas están en clases `@Nested` con `@DisplayName`. Las pruebas sí corren y sí se cuentan: el reporte `TEST-com.callejon9.kitchen.service.KitchenServiceTest.xml` lista los 24 casos sin fallas, la suma de los reportes da los 641 del total, y las mutaciones las hicieron fallar (sección 9). Una primera corrida con JDK 25 (10:54 a 10:59) dio 29 errores en pruebas de inventario, cobro, tickets y menú público, porque Mockito no puede simular clases concretas en Java 25; se descartó y se repitió con JDK 21, como en CI ([`backend-verify-jdk25-descartada.txt`](respuestas/backend-verify-jdk25-descartada.txt)).

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Ninguno en el código probado | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 5 | 5 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- CAL-127 se cerró en Linear el 2026-10-20 sin evidencia en `docs/pruebas/ut/`; esta evidencia se hizo después, el 2026-10-22, y amplía las pruebas de esa entrega.
- Las pruebas se ejecutaron con los cambios todavía sin commit; el commit y los checks del PR se anotan al abrirlo.
- La primera corrida de `mvnw verify` usó JDK 25 y se descartó por 29 errores ajenos a la cocina (C05). La válida es la de JDK 21, que también corrió SpotBugs.
- Las mutaciones del backend se corrieron con JDK 25: `KitchenServiceTest` solo simula interfaces y no le afecta ese problema de Mockito.
- La captura del equipo de trabajo (`entorno_trabajo_laptop.png`) es la misma de las SIT, tomada el 2026-10-21 a las 22:41. Se taparon con rectángulos grises la cuenta de Windows, el nombre del equipo, el Device ID, el Product ID y el número de serie, por ser datos personales.

**Conclusión del ejecutor.** Los cuatro criterios de CAL-127 quedan cubiertos con casos válidos, inválidos y de borde que fallan sin el comportamiento que protegen. Un producto de cocina solo avanza al siguiente estado y ninguna otra de las 13 combinaciones lo mueve, guarda o publica; la orden pasa a «Lista» solo cuando todos sus productos están listos o entregados, y una vez lista no se vuelve a guardar; y el semáforo y la etiqueta de espera cambian justo en 15 min, 25 min, 60 s y 60 min. Las pruebas de la primera entrega no detectaban 7 de las 15 mutaciones; ahora las 15 hacen fallar al menos una prueba.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/.../kitchen/service/KitchenServiceTest.java` (24 casos: 7 de la primera entrega y 17 nuevos, 13 de ellos en la matriz parametrizada), `frontend/src/test/kitchen-timing.test.ts` (13: 8 de la primera entrega y 5 nuevos) |
| Comando local | `cd backend; .\mvnw.cmd -B verify` y `cd frontend; pnpm test; pnpm lint; pnpm exec tsc --noEmit` |
| Resultado local | Backend: 641 pruebas en verde con JDK 21 y SpotBugs sin hallazgos. Frontend: 157 pruebas, lint y tipos en verde |
| Resultado en CI | Checks del PR |
| Prueba de que fallan sin el comportamiento | Quince mutaciones, todas detectadas: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Pruebas que fallan | ¿La detectaba la primera entrega? |
|---|---|---|
| M1 · se permiten los saltos hacia adelante | 4 | Sí |
| M2 · se permiten los retrocesos | 11 | Sí |
| M3 · se acepta el mismo estado | 4 | **No** |
| M4 · DELIVERED vuelve a PENDING | 1 | **No** |
| M5 · el producto cambia de estado antes de validar | 9 | **No** |
| M6 · no se valida que la orden exista | 1 | **No** |
| M7 · la orden pasa a READY con un solo producto listo | 1 | **No** |
| M8 · un producto DELIVERED no cuenta como listo | 3 | Sí |
| M9 · la orden se guarda aunque ya no esté en SENT | 1 | **No** |
| F1 · alerta con `>` en vez de `>=` | 1 | Sí |
| F2 · alerta a los 14 min | 1 | **No** |
| F3 · crítico con `>` en vez de `>=` | 1 | Sí |
| F4 · la etiqueta redondea en vez de truncar | 2 | Sí |
| F5 · pasa a horas después de 60 min | 1 | Sí |
| F6 · el semáforo no revisa la fecha nula | 1 | Sí |

La última columna sale de la misma corrida: una mutación «no» la detectaba la primera entrega cuando todas las pruebas que fallaron son nuevas.

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las pruebas fallan sin el comportamiento que protegen (sección 9)
- [ ] Los checks del PR pasan, incluido SpotBugs con Java 21
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las salidas no fueron editadas salvo para ocultar secretos, datos personales y rutas locales, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Braulio Hernández Mejía · **Fecha:** 2026-10-22
