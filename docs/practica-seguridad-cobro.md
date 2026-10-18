# Práctica: nada cambia en una orden mientras se cobra (CAL-105)

Módulo elegido: **órdenes, cobro y cocina**. El cobro (`CheckoutService.checkout`) toma una foto de las líneas de la orden, genera la venta y el ticket inmutable y marca la orden como `PAID`, todo en una sola transacción. El hallazgo es que otras operaciones sobre la misma orden podían colarse entre la foto y la confirmación.

## 1. Vulnerabilidad

| Campo | Detalle |
|---|---|
| Módulo | `backend/.../order/service/OrderService.java` (`addItems`, `cancelOrder`, `sendToKitchen`) frente a `backend/.../sale/service/CheckoutService.java` |
| Deficiencia | El cobro bloqueaba la orden, pero `addItems`, `cancelOrder` y `sendToKitchen` la leían sin bloqueo. Ninguna esperaba a que el cobro terminara. |
| Evidencia | Antes, las tres operaciones obtenían la orden con `findById`. Bajo `READ COMMITTED` leían el estado anterior al cobro (`NEW`), pasaban su validación y escribían sus cambios. Hibernate escribe la orden completa al confirmar, así que también reescribía el estado que el cobro acababa de poner. |
| Riesgo | **Integridad del cobro.** Un producto agregado durante el cobro quedaba en una orden pagada sin aparecer en el ticket: se entrega y nunca se cobra. Una orden cobrada podía terminar en `CANCELED`, con su venta registrada, o volver a `SENT` y reaparecer en cocina. |
| Prioridad | Media: no cruza restaurantes, pero descuadra la caja y el inventario con operaciones normales de un mesero. |

## 2. Corrección

Esta entrega no cambia el código de producción. El bloqueo que cierra el hallazgo ya está en `main` desde CAL-97 (commit `d5ba649`, PR #29). Lo que faltaba era una prueba que lo demostrara, y esta entrega la agrega.

| Decisión | Motivo |
|---|---|
| Todas las operaciones que modifican una orden la obtienen con `OrderRepository.findForCheckout` (`SELECT ... FOR UPDATE`): el cobro, `addItems`, `cancelOrder`, `sendToKitchen` y el avance de cocina | Comparten un mismo bloqueo de fila. La segunda operación espera a que la primera confirme y lee el estado ya confirmado. Si la orden quedó `PAID`, su validación la rechaza con `409 Conflict`. |
| El bloqueo se toma **antes** de validar el estado | Así la validación (`PAID`/`CANCELED`, o «no está en `NEW`») se hace sobre el estado actual y no sobre uno viejo. |
| Bloqueo pesimista en vez de una columna de versión | La orden ya se bloqueaba para impedir dos cobros simultáneos. Reusar el mismo bloqueo no requiere migración y no cambia el contrato de la API. |
| Las pruebas fijan el orden de la carrera | Una carrera real dura milisegundos. Las pruebas dejan la primera transacción abierta, arrancan la segunda y comprueban con `pg_blocking_pids` que es la transacción de la primera la que la bloquea antes de liberarla. Así el resultado no depende de la suerte ni de otras sesiones de la base. |

| Archivo | Cambio |
|---|---|
| `backend/src/test/java/com/callejon9/order/OrderChangesDuringCheckoutTest.java` | Nuevo. P1–P3 y dos pruebas complementarias. |

## 3. Pruebas

Las pruebas corren contra PostgreSQL 16 con los servicios reales. En cada una hay una orden abierta en una mesa con 2 «Tacos al pastor» de 100.00. El producto que se agrega es un «Refresco» de 35.00.

Para obtener el resultado **antes**, se cambió temporalmente `requireOrder` de `OrderService` a `findById`, como estaba antes de CAL-97, y se corrieron las mismas pruebas. Ese cambio se revirtió y no forma parte del commit.

### P1. Un producto agregado mientras se cobra no queda sin cobrar

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que el total del ticket es siempre la suma de las líneas de la orden, aunque se agregue un producto mientras se cobra. |
| Condiciones iniciales | Orden en estado `NEW` con 2 tacos (200.00). |
| Procedimiento | Se ejecuta el cobro y se deja su transacción sin confirmar. Mientras tanto, otro hilo agrega un refresco con `addItems`. Se comprueba que esa operación está esperando y se confirma el cobro. |
| Resultado esperado | `addItems` se rechaza con `BusinessRuleException` («ya está PAID»). El ticket y las líneas de la orden suman 200.00 y la orden queda `PAID`. |
| Resultado obtenido | **Antes:** falla con «el total del ticket debe ser la suma de las líneas de la orden: expected 235.00 but was 200.00». El refresco quedó en la orden pagada sin aparecer en el ticket (vulnerabilidad confirmada). **Después:** `addItems` espera el bloqueo y se rechaza; el ticket (200.00) cuadra con las líneas. |

### P2. Una orden que se está cobrando no se puede cancelar

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que una orden cobrada no termina cancelada por una cancelación simultánea. |
| Condiciones iniciales | Orden en estado `NEW` con 2 tacos. |
| Procedimiento | Se ejecuta el cobro sin confirmar y otro hilo llama a `cancelOrder`. Se comprueba que la cancelación espera y se confirma el cobro. |
| Resultado esperado | La cancelación se rechaza con `BusinessRuleException`. La orden queda `PAID` y su ticket cuadra con las líneas. |
| Resultado obtenido | **Antes:** falla con «una orden cobrada no debe terminar cancelada: expected "PAID" but was "CANCELED"». La orden quedó cancelada con una venta registrada. **Después:** la cancelación se rechaza y la orden queda `PAID`. |

### P3. Un producto agregado justo antes del cobro sí se cobra

| Campo | Detalle |
|---|---|
| Objetivo | Comprobar que el uso legítimo sigue funcionando: si el producto llega primero, el cobro lo espera y lo incluye. |
| Condiciones iniciales | Orden en estado `NEW` con 2 tacos. |
| Procedimiento | `addItems` agrega un refresco y su transacción queda sin confirmar. Otro hilo inicia el cobro. Se comprueba que el cobro espera y se confirma `addItems`. |
| Resultado esperado | El ticket incluye tacos y refresco, su subtotal es 235.00 y cuadra con las líneas de la orden. |
| Resultado obtenido | **Antes:** falla con «expected 235.00 but was 200.00». Sin el bloqueo en `addItems`, el cobro no esperaba: tomó la foto antes de que el refresco se confirmara, y el refresco quedó sin cobrar. **Después:** el cobro espera, el ticket incluye los dos productos y suma 235.00. |

P3 muestra que el hallazgo no dependía solo del orden «cobro primero». Sin bloqueo, el producto también se perdía cuando llegaba un instante antes del cobro.

### Pruebas complementarias

| Prueba | Qué comprueba | Resultado obtenido |
|---|---|---|
| Enviar a cocina mientras se cobra | `sendToKitchen` durante un cobro abierto se rechaza y la orden queda `PAID` | **Antes:** falla con «una orden cobrada no debe volver a la cocina: expected "PAID" but was "SENT"». **Después:** se rechaza. |
| Agregar y cobrar en paralelo, 10 rondas | La prueba que pide el hallazgo, sin fijar el orden: ambas operaciones arrancan a la vez. Gane quien gane, el ticket debe ser la suma de las líneas y, si `addItems` falla, debe ser por la regla de negocio | **Antes:** falla con 235.00 contra 200.00: en al menos una ronda el refresco quedó fuera del ticket. **Después:** las 10 rondas cuadran. |
| Pruebas existentes de órdenes y cobro | `OrderServiceConcurrencyTest` (dos meseros, una mesa) y `CheckoutControllerTest`, que incluye el cobro doble simultáneo | 1/1 y 12/12 en verde |

`OrderChangesDuringCheckoutTest` pasó 5/5 en tres corridas seguidas.

Prueba automatizada: `backend/src/test/java/com/callejon9/order/OrderChangesDuringCheckoutTest.java`.

Comando:

```powershell
cd backend; .\mvnw.cmd -B "-Dtest.database.mode=external" -DDB_PORT=5432 "-Dtest=OrderChangesDuringCheckoutTest,OrderServiceConcurrencyTest,CheckoutControllerTest" test
```

## 4. Riesgo residual

Cualquier operación nueva que modifique una orden o sus líneas debe obtener la orden con `findForCheckout` antes de validar su estado. Si usa `findById`, vuelve a abrir este hallazgo. Las pruebas de esta práctica cubren las operaciones que existen hoy.
