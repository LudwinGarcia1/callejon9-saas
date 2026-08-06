# Diseño — Inventario, bloque 1: insumos y movimientos manuales

- **Fecha:** 2026-08-06
- **Estado:** aprobado por el usuario
- **Antecede:** `2026-08-01-inventario-y-seguimiento-design.md`, bloque 1 de su orden de construcción
- **Punto de partida:** 128 pruebas verdes, 25 rutas de API, aislamiento multi-inquilino impuesto por PostgreSQL

## Contexto

El documento de hoja de ruta dejó el módulo de inventario diseñado y sin implementar. Este spec cubre su primer bloque: **insumos, movimientos manuales y alerta de mínimo**. Es la mitad del valor del módulo y la parte sin decisiones difíciles pendientes — recetas y descuento automático (bloque 3) son otro incremento, y este no los adelanta en nada.

El plano de datos ya está completo. `V2__data_plane.sql` creó `inventory_items` e `inventory_movements` con sus índices por inquilino; `V3` y `V5` les aplicaron la política `tenant_isolation` mediante un bucle sobre una lista de tablas que **ya las incluye**; `V4` otorgó DML al rol `callejon9_app`, que no es dueño de ninguna tabla y por lo tanto queda sujeto a RLS. Lo único que falta en la base es una columna, y es consecuencia de una decisión de este spec, no del esquema original.

---

## 1. Decisiones tomadas

Cinco preguntas se cerraron antes de escribir código. Cada una tenía dos respuestas defendibles y la elegida define cómo se comporta el módulo.

| Pregunta | Elección | Por qué |
|---|---|---|
| ¿El stock puede cambiar sin dejar movimiento? | **Nunca** | El historial explica siempre el stock actual, y `Σ movimientos == stock` se sostiene como invariante |
| ¿Qué guarda un ajuste por conteo físico? | **La diferencia con signo** | El ledger se puede reproducir; el conteo se conserva en el motivo |
| ¿Se puede dar de baja un insumo? | **Sí, con migración `V7`** | Evita repetir el defecto que el incremento anterior existió para cerrar: un catálogo que solo crece |
| ¿Se puede cambiar la unidad de un insumo con movimientos? | **No, queda fija** | Sin esto, un `20` registrado en kilos se convierte en gramos sin que nada lo advierta |
| ¿Dónde vive en la interfaz? | **Sección propia `/inventory`, `ADMIN` y `KITCHEN`** | `KITCHEN` tiene permiso de registrar movimientos y nunca ve `/admin` |

La primera decisión tiene una consecuencia que conviene explicitar porque ordena el resto del diseño: **el alta de un insumo con stock inicial genera un movimiento `IN`**, y el `PUT` de corrección no toca el stock. No hay ningún camino por el que la columna `stock` cambie sin una fila que lo justifique.

La segunda tiene otra: si el ajuste guarda el delta, *alguien* tiene que restar `conteo − stock actual`, y ese alguien no puede ser el cliente. Leería un stock que quizá ya cambió y dejaría el inventario en un número que nadie contó. El cálculo ocurre en el servidor, con el lock puesto.

### Estructura del módulo

Dos servicios, con la aritmética del stock en la entidad. Se descartaron un servicio único con la aritmética en el servicio (deja fuera de la capa Models un comportamiento inherente de la entidad) y un servicio único con la aritmética en la entidad (correcto pero mezcla dos razones de cambio en un archivo).

La razón de fondo para separar no es estética: cuando llegue el bloque 3, `KitchenService` dependerá de `InventoryMovementService` y de nada más. Es la diferencia entre agregar una llamada y tener que razonar sobre qué otra cosa quedó al alcance de la cocina.

---

## 2. Migración

```sql
-- V7__inventory_item_active.sql
ALTER TABLE inventory_items
    ADD COLUMN active boolean NOT NULL DEFAULT true;
```

Nada más. La política RLS de la tabla no cambia, y los permisos de `callejon9_app` ya cubren la columna nueva porque se otorgaron a nivel de tabla.

Es la única desviación respecto al documento de hoja de ruta, que anticipaba el bloque 1 sin migraciones. La baja lógica sigue el patrón que ya tienen productos, mesas y usuarios: `active = false`, nunca borrado físico, porque un insumo está referenciado por los movimientos que lo tocaron y borrarlo perdería el histórico.

---

## 3. Dominio

Paquete `com.callejon9.inventory`, con la misma estructura interna que `catalog`: `domain`, `repository`, `service`, `web`, `web.dto`.

```
InventoryItem extends TenantScopedEntity
    String     name        // varchar(160), UNIQUE (tenant_id, name) ya existe
    String     unit        // varchar(20)
    BigDecimal stock       // numeric(12,3)
    BigDecimal minStock
    BigDecimal unitCost
    boolean    active      // V7

InventoryMovement extends TenantScopedEntity
    UUID                  inventoryItemId
    InventoryMovementType movementType   // @Enumerated(STRING)
    BigDecimal            quantity       // con signo solo en ADJUSTMENT
    String                reason
    UUID                  userId
```

Las llaves foráneas viajan como `UUID` planos, sin `@ManyToOne`, igual que `Product.categoryId` y `Sale.cashierId`. `TenantScopedEntity` asigna el `tenant_id` en su `@PrePersist`, así que ningún servicio tiene que recordarlo.

### El signo vive en el enum

Es la única pieza que sabe qué significa cada tipo, y ponerlo aquí evita que el `switch` se reparta por el servicio:

```java
enum InventoryMovementType {
    IN, OUT, ADJUSTMENT, WASTE;

    BigDecimal signedEffect(BigDecimal quantity) {
        return switch (this) {
            case IN -> quantity;
            case OUT, WASTE -> quantity.negate();
            case ADJUSTMENT -> quantity;   // ya llega con signo
        };
    }
}
```

### La mutación y el nivel viven en la entidad

```java
void apply(InventoryMovementType type, BigDecimal quantity) {
    this.stock = this.stock.add(type.signedEffect(quantity));
}

StockLevel level() {
    if (stock.signum() < 0)                                    return NEGATIVE;
    if (minStock.signum() > 0 && stock.compareTo(minStock) <= 0) return LOW;
    return OK;
}
```

Dos detalles con consecuencia:

**`NEGATIVE` es un estado propio, no un caso de `LOW`.** El diseño original decidió que el stock negativo se permite y se grita: es la señal de que el conteo físico necesita corregirse, y merece verse distinto de "se está acabando". `apply` nunca lanza ni topa en cero — negarse a registrar el movimiento no produce la cebolla que el cocinero tiene en la mano.

**La alerta de mínimo exige `minStock > 0`.** La columna tiene `DEFAULT 0`; sin esa condición, todo insumo recién creado con stock 0 y mínimo 0 aparecería en alerta, y la lista de alertas nacería llena de ruido. `minStock = 0` significa "no configuré mínimo", no "avísame siempre".

### Concurrencia

`InventoryItemRepository` expone el mismo finder que ya usan `TenantRepository` y `RestaurantTableRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from InventoryItem i where i.id = :id")
Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
```

Sin ese lock, dos salidas simultáneas sobre el mismo insumo bajo READ COMMITTED leen el mismo stock, calculan sobre el mismo valor y una sobrescribe a la otra: el ledger registra dos movimientos y el stock refleja uno. Rompe exactamente la invariante que la primera decisión existe para sostener.

Ningún repositorio filtra por `tenant_id`; RLS delimita las filas.

---

## 4. Servicios

### `InventoryItemService` — el catálogo

| Método | Reglas |
|---|---|
| `listItems(includeInactive)` | Ordenado por nombre. Por defecto solo activos; `true` para poder reactivar, mismo criterio que productos y mesas |
| `createItem(...)` | Nombre duplicado → `409`. Con `initialStock > 0`, delega en `InventoryMovementService` |
| `updateItem(...)` | No existe → `404`. Nombre chocando con otro → `409`. Unidad distinta con movimientos → `409`. **No toca `stock`** |
| `setActive(id, active)` | Baja lógica |

`initialStock` es opcional y su ausencia equivale a 0: dar de alta un insumo que todavía no llega es el caso normal, no la excepción. Cuando viene, el movimiento `IN` que se genera lleva `"Stock inicial"` como motivo, para que el ledger diga de dónde salió esa primera cantidad en lugar de mostrar una entrada sin explicación.

La dependencia `InventoryItemService → InventoryMovementService` existe solo por el stock inicial y va en esa dirección, no la inversa. Es una transacción, no dos: si el movimiento falla, el insumo no queda creado con un stock que nada explica.

### `InventoryMovementService` — el ledger

`register(itemId, type, quantity, countedStock, reason, userId)`, en este orden:

1. `findByIdForUpdate(itemId)` — el lock **antes** de leer el stock. `404` si no existe.
2. Insumo inactivo → `409`. Si hay que dejarlo en cero, se ajusta antes de darlo de baja.
3. Se resuelve la cantidad efectiva según el tipo.
4. `item.apply(type, effectiveQuantity)`; se guardan insumo y movimiento.

**Cantidades:**

- `IN`, `OUT`, `WASTE`: cantidad estrictamente positiva. El signo lo pone el tipo, nunca el cliente.
- `ADJUSTMENT`: el delta se calcula como `countedStock − stock`. Si sale cero → `409`; el conteo coincide y una fila de cero en el ledger solo estorba.

**Motivos.** Que `WASTE` exija motivo es una regla de forma de la petición y la aplica el validador del DTO (§5), no el servicio: una merma sin motivo no sirve para lo único que las mermas existen para responder. Lo que sí hace el servicio es componer el motivo almacenado de un `ADJUSTMENT` — `"Conteo fisico: 8"`, concatenando lo que el usuario escribió si escribió algo. Así el ledger guarda el delta **y** el número contado, que era el dato que el delta solo no conserva.

`listMovements(from, to, itemId)` devuelve el más reciente primero, como el historial de ventas, y reutiliza `BusinessCalendar` con sus mismas reglas — sin parámetros, hoy; con uno solo, ese día — porque un movimiento registrado a las 19:00 de México cae en el día siguiente en UTC y el listado se vaciaría a media cena. Es la trampa que ya costó el commit `9b0e303`. `itemId` es un filtro opcional.

---

## 5. Superficie HTTP

Dos controladores, uno por recurso, como `ProductController` / `CategoryController`.

| Método | Ruta | Rol | Notas |
|---|---|---|---|
| `GET` | `/api/v1/inventory/items` | autenticado | `?includeInactive=false` por defecto |
| `POST` | `/api/v1/inventory/items` | `ADMIN` | `201` |
| `PUT` | `/api/v1/inventory/items/{id}` | `ADMIN` | nombre, unidad, mínimo, costo |
| `PATCH` | `/api/v1/inventory/items/{id}` | `ADMIN` | alta / baja lógica |
| `POST` | `/api/v1/inventory/movements` | `ADMIN`, `KITCHEN` | `201` |
| `GET` | `/api/v1/inventory/movements` | autenticado | `?from=&to=&itemId=` |

El `PATCH` no está en la tabla del documento original: sale de la decisión de agregar `active`. Los otros cinco son los de la hoja de ruta, con las mismas rutas y los mismos roles.

El `userId` de un movimiento **nunca** viene en el cuerpo: sale de `authentication.getPrincipal()`, igual que en `CheckoutController`.

### Peticiones

```java
record CreateInventoryItemRequest(name, unit, minStock, unitCost, initialStock)
record UpdateInventoryItemRequest(name, unit, minStock, unitCost)
record UpdateInventoryItemStatusRequest(active)

@ValidMovementRequest
record RegisterMovementRequest(
    @NotNull InventoryMovementType movementType,
    @Positive BigDecimal quantity,             // IN / OUT / WASTE
    @PositiveOrZero BigDecimal countedStock,   // ADJUSTMENT
    @Size(max = 150) String reason)
```

El cuerpo del movimiento lleva **dos campos y cada uno significa una sola cosa**:

```
IN / OUT / WASTE    { movementType, quantity: > 0, reason }
ADJUSTMENT          { movementType, countedStock: >= 0, reason }
```

Se descartó un solo campo `quantity` que significara "cantidad" en tres tipos y "conteo físico" en el cuarto. Se lee más corto y es la clase de ambigüedad que después se paga leyendo el servicio para saber qué guardó cada fila.

**Qué campo exige cada tipo se valida con una restricción de clase, no en el servicio.** `@ValidMovementRequest` es un `ConstraintValidator` de unas 25 líneas que cubre la regla cruzada — `quantity` junto con `ADJUSTMENT` es inválido, `countedStock` sin él también, y `WASTE` exige motivo. Devuelve `400` con el mapa `errors` que `GlobalExceptionHandler` ya produce y el frontend ya sabe leer.

La alternativa era ponerlo en el servicio y lanzar `BusinessRuleException`, que mapea a `409` — y enviar el campo equivocado es una solicitud mal formada, no un conflicto de negocio. `InvalidRoleException → 400` es el precedente de esta distinción en el proyecto. Además, la validación estricta de forma es la capa que `CLAUDE.md` asigna a los DTOs; el servicio conserva las reglas que sí son de negocio: delta cero, insumo inactivo, unidad bloqueada, nombre duplicado.

`reason` se limita a 150 caracteres para que el prefijo `"Conteo fisico: …"` quepa en el `varchar(200)` de la columna.

### Respuestas

```java
record InventoryItemResponse(
    UUID id, String name, String unit,
    BigDecimal stock, BigDecimal minStock, BigDecimal unitCost,
    boolean active, StockLevel level)

record InventoryMovementRow(
    UUID id, UUID inventoryItemId, String itemName, String unit,
    InventoryMovementType movementType, BigDecimal quantity,
    String reason, String userName, Instant createdAt)
```

El nivel de stock viaja calculado: la interfaz pinta una insignia sin recalcular umbrales de negocio.

El listado de movimientos es una proyección, no la entidad. Se construye con expresión constructora en JPQL y `on` explícito, como `SaleRepository.findHistory`. `userName` es nulable — la llave es `ON DELETE SET NULL` y el usuario puede estar dado de baja — mientras que el join al insumo es interno, porque su llave es `NOT NULL ON DELETE CASCADE` y un movimiento sin insumo no existe. El filtro opcional entra como `and (:itemId is null or m.inventoryItemId = :itemId)`.

Los errores caen en el `GlobalExceptionHandler` existente sin tocarlo: `ResourceNotFoundException` → `404`, `BusinessRuleException` → `409`, Bean Validation → `400`.

---

## 6. Frontend

Sección propia en `src/app/(authenticated)/inventory/`:

```
page.tsx
inventory-view.tsx              dos pestañas: Insumos y Movimientos
create-item-dialog.tsx
edit-item-dialog.tsx
register-movement-dialog.tsx    el unico con logica de interes
```

Se tocan `lib/types.ts` (tipos y etiquetas en español), `lib/endpoints.ts`, `lib/query-keys.ts`, `components/layout/app-sidebar.tsx` y `components/shared/status-badge.tsx`.

### Pestaña *Insumos*

Tres tarjetas: **Insumos**, **En alerta** (`LOW` + `NEGATIVE`) y **Valor del inventario** (`Σ stock × unitCost`), derivadas de la misma lista que alimenta la tabla, como `admin-view` deriva el total de hoy. La tercera existe por una razón concreta: `unit_cost` se captura en el alta y sin ella el módulo pediría un dato que después no aparece en ninguna pantalla.

Tabla con nombre, unidad, stock con su insignia de nivel, mínimo, costo, estado y acciones. La fila en `NEGATIVE` lleva fondo tenue: es la señal que el diseño pidió que se gritara, y una insignia sola se pierde en una lista de treinta insumos.

**Por rol:** `KITCHEN` entra a la sección pero no ve *Nuevo insumo*, *Editar* ni *Dar de baja*, porque esos tres endpoints son `ADMIN`. Le queda *Registrar movimiento*. Mismo criterio que el sidebar: la interfaz refleja la autoridad que el servidor aplica, en vez de ofrecer un botón que devolvería `403`.

### `register-movement-dialog.tsx`

El selector de tipo cambia el formulario:

| Tipo | Campo | Motivo |
|---|---|---|
| Entrada / Salida | `quantity` | opcional |
| Merma | `quantity` | **obligatorio** |
| Ajuste | `countedStock` | opcional |

Con *Ajuste* seleccionado, el diálogo muestra el stock actual y la diferencia que resultaría, **etiquetada como estimación**: el número que manda es el que calcula el servidor con el lock puesto. El cliente enseña una previsualización, no manda un delta.

### Pestaña *Movimientos*

Rango de fechas con el formulario del historial de ventas (`desde`/`hasta`, hoy por defecto) más un selector de insumo para el filtro opcional. Tabla con fecha, insumo, tipo, cantidad con signo, motivo y quién lo registró.

Al registrar un movimiento se invalidan las dos claves — insumos y movimientos — porque el stock de la primera pestaña cambió.

### `status-badge.tsx`

Se extiende con `kind: "stock"` y `kind: "movement"`, más un mapa de `variant` para que `NEGATIVE` salga en `destructive`. Hoy el componente pinta todo en `secondary` y los demás `kind` no cambian de aspecto. Las etiquetas siguen viviendo solo en `lib/types.ts`.

### Navegación

`NAV_INVENTORY` se agrega a `ADMIN` y a `KITCHEN` en `NAV_ITEMS_BY_ROLE`.

---

## 7. Pruebas

Cuatro clases nuevas. Cada caso se escribe antes de su implementación.

**`InventoryItemTest`** — unitaria, sin Spring. Prueba lo que vive en la entidad:

- `IN` suma; `OUT` y `WASTE` restan; `ADJUSTMENT` suma el delta con signo
- `level()` es `NEGATIVE` con stock bajo cero, incluso con mínimo 0
- `level()` es `LOW` con stock igual al mínimo, y `OK` con mínimo 0 y stock 0
- el stock puede quedar negativo: `apply` no lanza ni topa en cero

**`InventoryItemControllerTest`** — `@SpringBootTest` + MockMvc, sembrando con `TenantOnboardingService` y cookie JWT, como `ProductControllerTest`:

- `POST` como `ADMIN` → `201`, stock 0
- `POST` con `initialStock` → el stock queda en ese valor **y existe un movimiento `IN`**
- nombre duplicado → `409`; `WAITER` → `403`
- `PUT` conserva el stock intacto
- `PUT` cambiando unidad sin movimientos → `200`; con movimientos → `409`
- `PATCH` da de baja; el listado por defecto lo oculta y `includeInactive=true` lo muestra

**`InventoryMovementControllerTest`**:

- los cuatro tipos con su efecto sobre el stock
- `countedStock` 8 sobre stock 11 guarda `-3` y el motivo empieza con `Conteo fisico: 8`
- ajuste cuyo conteo coincide con el stock → `409`
- `quantity` junto con `ADJUSTMENT` → `400` con el campo en `errors`; `WASTE` sin motivo → `400`
- movimiento sobre insumo inactivo → `409`; insumo inexistente → `404`
- `KITCHEN` puede registrar; `WAITER` → `403`
- el listado filtra por rango y por `itemId`, y un movimiento de las 19:00 locales aparece en el día local, no en el siguiente

**`InventoryMovementConcurrencyTest`** — el patrón de `OrderServiceConcurrencyTest`: dos salidas simultáneas sobre el mismo insumo. Se afirma que el stock final refleja **ambas** y que `Σ movimientos == stock`. Sin el lock pesimista esta prueba falla, que es lo que la hace valer.

**Sin prueba de aislamiento nueva.** La política `tenant_isolation` se crea con un bucle sobre una lista de tablas que ya incluye ambas tablas de inventario, y `TenantIsolationTest` prueba el mecanismo. Una prueba por tabla sería ceremonia, no evidencia.

El frontend se verifica en navegador, como el resto del proyecto.

---

## 8. Fuera de alcance

**Proveedores, recetas y descuento automático.** Bloques 2 y 3 de la hoja de ruta. Este spec no adelanta nada de ellos: no crea tablas, columnas ni ganchos "por si acaso".

**Conversión de unidades.** Explicado en el documento original: parece trivial y no lo es. Aquí se refuerza con la regla de unidad fija, que hace explícito el costo de haber elegido mal la unidad en lugar de esconderlo.

**Movimiento `OUT` automático desde cocina.** Es el bloque 3 completo. El `OUT` de este bloque es siempre manual.

**Costeo de recetas y margen por platillo.** La tarjeta de valor del inventario usa `unit_cost`, pero es una suma, no un costeo.
