# Evidencia de prueba · CAL-125 — UT · Pedidos del mesero

> **v1.** Ejecución completa; pendiente de revisión por otro integrante (sección 10).

## 1. Identificación

| Campo | Valor |
|---|---|
| Issue | [CAL-125](https://linear.app/callejon19/issue/CAL-125/ut-pedidos-del-mesero) — UT · Pedidos del mesero |
| Historia o issue padre | [CAL-119](https://linear.app/callejon19/issue/CAL-119/ut-cerrar-los-huecos-de-pruebas-unitarias-por-modulo) — UT · Cerrar los huecos de pruebas unitarias por módulo |
| Tipo de prueba | UT |
| Módulo | Pedidos del mesero (`order/service/OrderService`, DTO de `order/web/dto`, `frontend/.../waiter/order/[id]/order-view.tsx`) |
| Entorno | Local y CI |
| Commit probado | Head del PR de esta evidencia; pruebas ejecutadas sobre `602ad2d` y `c2fe3c6` (rama desde `main` `68e6e42`) |
| Ejecución de CI | Checks del PR |
| Navegador / dispositivo | No aplica: JDK 25 con `--release 21`, Maven y Testcontainers (PostgreSQL 16 en Docker 29.7.2); Node 24 con Vitest 3 y jsdom |
| Versión de esta evidencia | v1 · 2026-10-21 |

## 2. Responsables

| Rol | Persona | Notas |
|---|---|---|
| Responsable del issue | Octavio Duarte | Asignado en Linear |
| Ejecutor de la prueba | Octavio Duarte | Pruebas y mutaciones de este PR |
| Revisor | *Pendiente* | **Distinto del ejecutor.** Llena la sección 10 |

## 3. Fechas

Todas las horas en `America/Mexico_City`, formato `AAAA-MM-DD HH:MM`.

| Elemento | Inicio planeado | Fin planeado | Inicio real | Fin real | Desviación |
|---|---|---|---|---|---|
| Esta tarea (CAL-125) | | | 2026-10-21 15:43 | | — |
| Ejecución de las pruebas | 2026-10-21 | 2026-10-21 | 2026-10-21 16:35 | 2026-10-21 16:55 | — |

## 4. Alcance y criterios cubiertos

**Objetivo.** Cubrir con pruebas automatizadas las reglas de las órdenes del mesero que no tenían prueba unitaria: el total calculado con los precios del servidor, los topes de CAL-112 en su borde exacto, el rechazo de productos inactivos (CAL-10) y el comportamiento del carrito de la web.

**Fuera de alcance.** La propiedad de la orden entre meseros (CAL-111), que ya cubre `WaiterOrderSecurityTest`, y el mensaje del 403 (CAL-166).

| Criterio del issue | Caso(s) que lo cubren |
|---|---|
| Total con varios productos y cantidades, calculado con el precio del servidor | C01 |
| Topes de CAL-112 en el borde: cantidad 99/100, 50/51 líneas por lote, notas de 500/501 caracteres, comensales 50/51 | C02 |
| Producto inactivo agregado a una comanda (CAL-10) | C03 |
| Carrito de `order-view`: tope de 99 por línea y cancelación según el rol | C04 |
| Criterio de salida: casos válido, inválido y de borde por regla; CI en verde | C01 a C05 |

## 5. Preparación

- [x] Docker Desktop encendido para las pruebas de integración del backend (Testcontainers)
- [x] `OrderService` con repositorios simulados (Mockito); los DTO con el validador real de Jakarta Bean Validation
- [x] `OrderView` montado con `@testing-library/react`; el selector de productos se sustituye por uno mínimo que expone sus acciones y `useSession` se simula por rol

## 6. Casos de prueba

### C01 — El total usa los precios del servidor

| Campo | Valor |
|---|---|
| Criterio cubierto | Total con el precio del servidor |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:35 / 2026-10-21 16:37 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Agregar 2× Guacamole ($95) y 1× Margarita ($135); el cliente solo envía producto y cantidad | — | Total $325.00, con el precio y el nombre del catálogo en cada línea | Aprobado | `OrderServiceItemsTest.totalUsesServerPricesAndQuantities` |
| 2 | Agregar un segundo lote (3× Flan, $75) a una orden con 1× Sopa ($85) | — | Total $310.00 | Aprobado | `secondBatchAddsToTheExistingTotal` |
| 3 | Cambiar el precio del producto después de agregarlo | $135 → $150 | La línea conserva $135 | Aprobado | `priceIsSnapshottedOnTheLine` |
| 4 | Agregar a una orden lista para entregar | Estado READY | Regresa a «Enviada a cocina» y se anuncia otra vez a la cocina | Aprobado | `addingToAReadyOrderSendsItBackToTheKitchen` |

### C02 — Los topes de CAL-112 rechazan exactamente desde el primer valor fuera del límite

| Campo | Valor |
|---|---|
| Criterio cubierto | Topes de CAL-112 |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:35 / 2026-10-21 16:37 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Regla | Último valor aceptado | Primer valor rechazado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Cantidad por línea | 1 y 99 | 0 y 100 | Aprobado | `OrderRequestLimitsTest.quantityLimits` |
| 2 | Notas | 500 caracteres y sin nota | 501 caracteres | Aprobado | `notesLimits` |
| 3 | Líneas por lote | 1 y 50 | 0 y 51 | Aprobado | `batchSizeLimits` |
| 4 | Cantidad dentro de un lote | — | `items[1].quantity` = 100 | Aprobado: señala la línea exacta | `batchValidatesEachLine` |
| 5 | Comensales | 1 y 50 | 0 y 51 | Aprobado | `guestCountLimits` |

### C03 — Un producto inactivo no entra a la comanda

| Campo | Valor |
|---|---|
| Criterio cubierto | Producto inactivo (CAL-10) |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:35 / 2026-10-21 16:37 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Lote con un producto activo y uno inactivo | «Pozole de temporada» inactivo | «El producto Pozole de temporada esta inactivo.» y no se guarda ninguna línea del lote | Aprobado | `inactiveProductIsRejected` |
| 2 | Producto que no existe en el restaurante | UUID desconocido | 404 «no existe» | Aprobado | `unknownProductIsRejected` |
| 3 | Orden pagada o cancelada | PAID y CANCELED | «no admite mas productos» | Aprobado | `closedOrdersRejectNewItems` |

### C04 — El carrito de la web respeta el tope y la cancelación depende del rol

| Campo | Valor |
|---|---|
| Criterio cubierto | Carrito de `order-view` |
| Prioridad | Media |
| Rol que ejecuta | WAITER y ADMIN simulados |
| Inicio / fin de ejecución | 2026-10-21 16:38 / 2026-10-21 16:55 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | Subir la cantidad de un producto | 98 clics y luego más | Se detiene en 99; ni «+» ni volver a agregar lo pasan | Aprobado | [`frontend.txt`](respuestas/frontend.txt) |
| 2 | Confirmar el lote | — | `POST …/items` con `quantity: 99` | Aprobado | |
| 3 | Botón «Cancelar orden» por rol y estado | 8 combinaciones | Mesero: solo en «Nueva». Administrador: «Nueva», «Enviada a cocina» y «Lista»; nunca en «Pagada» ni «Cancelada» | Aprobado en las 8 | |

### C05 — Puertas completas del backend y del frontend

| Campo | Valor |
|---|---|
| Criterio cubierto | Criterio de salida |
| Prioridad | Alta |
| Rol que ejecuta | No aplica |
| Inicio / fin de ejecución | 2026-10-21 16:46 / 2026-10-21 16:55 |
| Estado | ✅ Aprobado |
| Defecto vinculado | — |

| # | Acción | Dato usado | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|---|
| 1 | `.\mvnw.cmd -B verify` | PostgreSQL 16 con Testcontainers | Todas las pruebas en verde | **636 de 636**. SpotBugs no corre en local con JDK 25 | [`backend-verify.txt`](respuestas/backend-verify.txt) |
| 2 | `pnpm test` (dos corridas), `pnpm lint`, `pnpm exec tsc --noEmit` | — | Sin fallas | **161 de 161** en ambas; lint y tipos sin errores | [`frontend.txt`](respuestas/frontend.txt) |

**Observaciones.** Una primera corrida de `pnpm test`, hecha mientras el `mvnw verify` usaba la máquina, dio 4 fallas que no se reprodujeron en tres corridas posteriores. Las dos pruebas del tope del carrito tardaban unos 10 s cada una con la máquina cargada, así que se fusionaron en una (de 19 s a 1.6 s) y se repitieron las mutaciones M8 y M9 sobre la versión fusionada.

## 7. Defectos encontrados

| ID | Caso | Severidad | Descripción breve | Estado | Re-probado el |
|---|---|---|---|---|---|
| — | — | — | Ninguno | — | — |

## 8. Resumen

| Total | ✅ Aprobados | ❌ Fallidos | ⛔ Bloqueados | ⏭️ No ejecutados |
|---|---|---|---|---|
| 5 | 5 | 0 | 0 | 0 |

**Desviaciones y limitaciones.**

- La etapa SpotBugs se valida en CI con Java 21, como explica C05.
- El selector de productos se sustituyó en la prueba del carrito; su agrupación por categoría queda fuera de esta prueba.

**Conclusión del ejecutor.** Los cuatro criterios de CAL-125 quedan cubiertos con pruebas que fallan sin el comportamiento que protegen. El total sale de los precios del servidor y se congela en cada línea; los topes de CAL-112 rechazan justo desde el primer valor fuera del límite; un producto inactivo invalida el lote completo; y la web limita la línea a 99 y muestra «Cancelar orden» según el rol, igual que el backend.

## 9. Anexo UT — pruebas unitarias

| Campo | Valor |
|---|---|
| Clases o archivos de prueba | `backend/.../order/service/OrderServiceItemsTest.java` (7), `backend/.../order/web/dto/OrderRequestLimitsTest.java` (5), `frontend/src/test/order-cart.test.tsx` (9) |
| Comando local | `cd backend; .\mvnw.cmd -B verify` y `cd frontend; pnpm test; pnpm lint; pnpm exec tsc --noEmit` |
| Resultado local | Backend: 636 pruebas en verde. Frontend: 161 pruebas, lint y tipos en verde |
| Resultado en CI | Checks del PR |
| Prueba de que fallan sin el comportamiento | Nueve mutaciones, todas detectadas: [`respuestas/mutaciones.txt`](respuestas/mutaciones.txt) |

| Mutación | Pruebas que fallan |
|---|---|
| M1 · el precio de la línea sale de una constante, no del producto | 3 |
| M2 · se acepta un producto inactivo | 1 |
| M3 · el total ignora la cantidad | 2 |
| M4 · la cantidad admite 100 | 2 |
| M5 · el lote admite 51 líneas | 1 |
| M6 · las notas admiten 501 caracteres | 1 |
| M7 · los comensales admiten 51 | 1 |
| M8 · el carrito no tiene tope | 1 (con la prueba fusionada) |
| M9 · el mesero puede cancelar órdenes enviadas | 2 |

## 10. Revisión

- [ ] Cada criterio del issue tiene al menos un caso con resultado
- [ ] Las pruebas fallan sin el comportamiento que protegen (sección 9)
- [ ] Los checks del PR pasan, incluido SpotBugs con Java 21
- [ ] No hay contraseñas, tokens, cookies ni datos personales

| Revisor | Fecha | Veredicto | Observaciones |
|---|---|---|---|
| | | | |

## 11. Declaración de autenticidad

Declaro que esta evidencia proviene de una ejecución real en el entorno y la fecha indicados, que las salidas no fueron editadas salvo para ocultar secretos y rutas locales, y que los casos marcados como no ejecutados no se ejecutaron.

**Ejecutor:** Octavio Duarte · **Fecha:** 2026-10-21
