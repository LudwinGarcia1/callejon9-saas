# Diseño — Inventario y seguimiento del pedido

- **Estado:** diseñado, no implementado. Hoja de ruta posterior a la entrega.
- **Punto de partida:** 128 pruebas verdes, 25 rutas de API, aislamiento multi-inquilino impuesto por PostgreSQL.

## Contexto

El sistema Flask original tenía un módulo de inventario y una pantalla de cocina. Al migrar se difirieron los dos, y este documento cierra esa deuda con un diseño en lugar de dejarla como un hueco.

Parte del trabajo ya está hecho en la base de datos. `V2__data_plane.sql` creó dos tablas con sus políticas RLS, que hoy no tienen ninguna clase Java detrás:

```sql
inventory_items      (id, tenant_id, name, unit, stock numeric(12,3),
                      min_stock, unit_cost, created_at, updated_at)
inventory_movements  (id, tenant_id, inventory_item_id, movement_type,
                      quantity, reason, user_id, created_at, updated_at)
                      movement_type IN ('IN','OUT','ADJUSTMENT','WASTE')
```

El vocabulario de movimientos ya cubre lo que hace falta: entradas, salidas, ajustes de conteo y mermas. Lo que no existe en ninguna parte es proveedores ni recetas.

---

## 1. La decisión que define el módulo

No es técnica, es de negocio, y hay que responderla antes de escribir una línea: **¿en qué momento se descuenta el inventario?**

Las tres respuestas son defendibles y cada una hace que la palabra "stock" signifique algo distinto.

| Momento | Qué significa el stock | Qué se rompe |
|---|---|---|
| Al tomar la comanda | Compromisos, no consumo | Cancelar obliga a devolver todo; el stock baja por platillos que quizá nunca se cocinen |
| **Al empezar a preparar el platillo** | Consumo real | Una comanda cancelada después de cocinada ya gastó los insumos, y nadie los devuelve |
| Al cobrar | Lo más simple | El stock miente durante todo el servicio: la cocina lleva horas gastando y el sistema no se entera |

**Elección: al pasar cada renglón a `IN_PREPARATION`.**

Es el momento en que los insumos salen físicamente del estante. Tres razones lo hacen mejor que las otras dos:

Es **por renglón, no por comanda**. Si una mesa pide tres platillos y solo se empezó uno, se descuenta uno. Ninguna de las otras opciones da esa granularidad sin trabajo extra.

Cae sobre una **transición que ya existe y ya es transaccional**: `KitchenService` mueve el renglón de `PENDING` a `IN_PREPARATION` dentro de una transacción. El descuento entra ahí sin inventar un momento nuevo.

Y hace que **cancelar sea honesto sin código especial**. Un renglón que sigue en `PENDING` no descontó nada, así que cancelar no devuelve nada. Un renglón que ya se estaba cocinando sí consumió, y cancelar la comanda no lo devuelve — porque en la realidad tampoco se devuelve. Opcionalmente se registra un movimiento `WASTE` con el motivo, que es exactamente para lo que ese tipo existe.

### Stock negativo: se permite, y se grita

Bloquear a la cocina porque el conteo está mal es peor que tener el conteo mal. Si el sistema dice que quedan cero cebollas y el cocinero tiene una cebolla en la mano, negarse a registrar el platillo no produce una cebolla: produce que dejen de usar el sistema.

Entonces: el movimiento siempre se registra, el stock puede quedar negativo, y la interfaz lo muestra de forma prominente. Un stock negativo no es un error del sistema, es **una señal de que el conteo físico necesita corregirse**, y ocultarla rechazando el movimiento no arregla la realidad.

La alerta de `min_stock` — que ya está en el esquema — cubre el caso preventivo.

---

## 2. Modelo de datos

### Lo que ya existe

`inventory_items` e `inventory_movements`, sin cambios.

### Migración nueva: proveedores

```sql
suppliers (id, tenant_id, name, contact_name, phone, email,
           active, created_at, updated_at)
           UNIQUE (tenant_id, name)
```

Más una columna nullable `supplier_id` en `inventory_movements`, con sentido únicamente en los movimientos de tipo `IN`.

**Órdenes de compra quedan fuera a propósito.** Un proveedor aquí es una atribución sobre una entrada — "estas 20 cajas de tomate vinieron de La Central" — no un flujo de aprovisionamiento con cotizaciones, pedidos y recepciones. Ese es otro proyecto.

### Migración nueva: recetas

```sql
product_ingredients (product_id, inventory_item_id, quantity numeric(12,3))
                     PRIMARY KEY (product_id, inventory_item_id)
```

Tenant-scoped con RLS, como todo el plano de datos.

Dos propiedades importantes de este diseño:

**Un platillo sin receta simplemente no descuenta.** La función es opcional por producto. Eso significa que activarla no rompe los datos que ya existen ni obliga a capturar recetas de todo el menú antes de poder usar el inventario para cualquier cosa.

**Sin conversión de unidades.** La cantidad de la receta se expresa en la unidad del insumo, tal cual. Si una receta necesita gramos, el insumo se define en gramos. Meter un sistema de conversiones —kilos a gramos, litros a mililitros, piezas a cajas— parece obvio y es donde estos módulos se atascan durante semanas. Es una limitación deliberada y se documenta como tal.

---

## 3. Superficie de API

| Método | Ruta | Rol |
|---|---|---|
| `GET` | `/api/v1/inventory/items` | autenticado |
| `POST` | `/api/v1/inventory/items` | `ADMIN` |
| `PUT` | `/api/v1/inventory/items/{id}` | `ADMIN` |
| `POST` | `/api/v1/inventory/movements` | `ADMIN`, `KITCHEN` |
| `GET` | `/api/v1/inventory/movements` | autenticado, con rango de fechas |
| `GET`/`POST`/`PUT` | `/api/v1/suppliers` | `ADMIN` |
| `GET`/`PUT` | `/api/v1/products/{id}/recipe` | `ADMIN` |

El descuento automático no tiene endpoint: ocurre dentro de la transición de cocina que ya existe.

Ningún repositorio lleva predicado de `tenant_id`; RLS delimita las filas, como en todo el resto.

---

## 4. Seguimiento del pedido para el cliente

La idea: el cliente ve en qué etapa va su pedido sin necesidad de cuenta.

### El problema que la propia arquitectura pone enfrente

Una página pública no tiene JWT. Sin JWT no hay `TenantContext`. Sin `TenantContext`, `TenantAwareTransactionManager` no publica `app.tenant_id`, y las políticas RLS —que leen `nullif(current_setting('app.tenant_id', true), '')::uuid`— devuelven cero filas.

**Esto no es un obstáculo, es la garantía funcionando.** Significa que una página pública no puede exponer datos por accidente: hay que publicar el inquilino de forma deliberada y auditable, o no se ve nada.

Pero genera un huevo-y-gallina: para encontrar el pedido por su código hay que consultar `orders`, que está protegida por RLS, que necesita el inquilino, que es justo lo que no se conoce todavía.

### La salida

**Poner el identificador del restaurante en la propia URL pública.**

```
/seguimiento/{slug}/{token}
```

El `slug` ya es público — aparece en la pantalla de login. La resolución queda:

1. Buscar el inquilino por `slug` en `tenants`, que **no tiene RLS** porque es el catálogo de la plataforma.
2. Publicar ese inquilino en `TenantContext`.
3. Buscar el pedido por su token, ya bajo RLS, con las mismas reglas que todo lo demás.

Se preserva la invariante que sostiene el proyecto: **RLS nunca se evade; el inquilino siempre se publica a propósito.**

La alternativa —una tabla de tokens sin RLS que mapee token a inquilino— también funciona, pero agrega una tabla y un lugar más donde el aislamiento depende de que alguien recuerde algo. La URL con slug no agrega nada nuevo al modelo.

### El token

Una columna `public_token uuid UNIQUE` en `orders`, generada al abrir la comanda.

No se usa el `id` del pedido aunque sea igual de imposible de adivinar: acopla la superficie pública a la llave interna y no se puede rotar si alguna vez hace falta.

### Qué muestra, y qué no

Muestra el folio, la etapa de cada renglón con sus etiquetas en español, y una etapa general derivada. Nada más.

No muestra el mesero que atendió, ni el número de mesa, ni datos de otros pedidos. El endpoint devuelve una proyección hecha para esto, no la entidad completa: es la diferencia entre exponer una vista y exponer una tabla.

---

## 5. Orden de construcción

Cada bloque sirve por sí solo, así que cortar a la mitad deja un sistema coherente.

1. **Insumos y movimientos manuales.** Las tablas ya existen, no hace falta migración. Entradas, salidas, mermas, ajustes y alerta de mínimo. Es la mitad del valor del módulo y la parte sin decisiones difíciles.
2. **Proveedores.** Migración, entidad y atribución en las entradas.
3. **Recetas y descuento automático.** La pieza con la decisión de negocio detrás. Debe llegar con pruebas del caso de cancelación, que es donde se equivoca.
4. **Seguimiento público.** Independiente de los tres anteriores; puede adelantarse si interesa más.

---

## 6. Fuera de alcance, y por qué

**Órdenes de compra y recepción de mercancía.** Un flujo de aprovisionamiento completo es un proyecto aparte, no un apéndice del inventario.

**Conversión de unidades.** Explicado arriba: parece trivial y no lo es.

**Costeo de recetas y margen por platillo.** Se puede calcular con lo que este diseño deja en la base —`unit_cost` por insumo y cantidades por receta— pero presentarlo bien es un tema de reportes, no de inventario.

**Inventario por sucursal.** El modelo actual asume un almacén por restaurante. Varias sucursales de un mismo inquilino son otro eje de particionamiento y merecen su propio diseño.
