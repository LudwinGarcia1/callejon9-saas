# Glosario español ↔ inglés

El código de este proyecto está en inglés y su documentación en español. Este archivo conecta ambos, para que el modelado original del sistema Flask —`MODELADO.md`, `MANUAL_TECNICO.md`— se pueda seguir dentro del código nuevo sin traducir mentalmente en cada archivo.

## Por qué el código está en inglés

En Java las anotaciones, los tipos y las librerías ya están en inglés. Escribir `ComandaService` junto a `@Transactional`, `Optional<Comanda>` y `findByEstado` produce clases mitad en un idioma y mitad en otro, que se leen peor que cualquiera de las dos opciones puras. La convención es universal en el ecosistema, y para un trabajo académico tiene la ventaja de que cualquier revisor externo puede leerlo.

La documentación se queda en español porque su público es distinto: el jurado, y el propio autor dentro de seis meses.

## Entidades del dominio

| Español (modelado original) | Inglés (código) | Tabla |
|---|---|---|
| Restaurante / inquilino | `Tenant` | `tenants` |
| Plan | `Plan` | `plans` |
| Suscripción | `Subscription` | `subscriptions` |
| Usuario / empleado | `User` | `users` |
| Token de refresco | `RefreshToken` | `refresh_tokens` |
| Mesa | `RestaurantTable` | `restaurant_tables` |
| Categoría | `Category` | `categories` |
| Producto / platillo | `Product` | `products` |
| Comanda | `Order` | `orders` |
| Renglón de comanda | `OrderItem` | `order_items` |
| Venta | `Sale` | `sales` |
| Ticket | `Ticket` | `tickets` |
| Insumo | `InventoryItem` | `inventory_items` |
| Movimiento de inventario | `InventoryMovement` | `inventory_movements` |

`RestaurantTable` no se llama `Table` porque `TABLE` es palabra reservada en SQL y el nombre corto genera fricción constante con JPA y con las herramientas.

`InventoryMovement` no se llama `StockMovement` porque la tabla del esquema original ya se llamaba `inventory_movements`, y renombrar el concepto en el código para que dejara de coincidir con la tabla habría creado exactamente la fricción que este glosario existe para evitar.

`Product` unifica dos colecciones del sistema original —`productos` y `platillos`— que modelaban el mismo concepto por duplicado.

### Tablas sin entidad todavía

Existen en el esquema, con sus políticas RLS aplicadas, pero ninguna clase Java las mapea porque su funcionalidad quedó diferida: `customers`, `payments`, `inventory_items`, `inventory_movements` y `notifications`.

## Estados

Los estados eran cadenas sueltas en el sistema original —constantes de clase o literales dispersos—. Ahora son enumeraciones de Java respaldadas por restricciones `CHECK` en la base.

| Concepto | Enumeración | Valores | Etiqueta en pantalla |
|---|---|---|---|
| Estado de comanda | `OrderStatus` | `NEW` | Nueva |
| | | `SENT` | Enviada a cocina |
| | | `READY` | Lista |
| | | `PAID` | Pagada |
| | | `CANCELED` | Cancelada |
| Estado en cocina | `KitchenItemStatus` | `PENDING` | Pendiente |
| | | `IN_PREPARATION` | En preparación |
| | | `READY` | Listo |
| | | `DELIVERED` | Entregado |
| Estado de mesa | `TableStatus` | `FREE` | Libre |
| | | `OCCUPIED` | Ocupada |
| | | `RESERVED` | Reservada |
| | | `CLEANING` | En limpieza |
| Estado de venta | `SaleStatus` | `PENDING`, `COMPLETED`, `CANCELED` | |
| Método de pago | `PaymentMethod` | `CASH` | Efectivo |
| | | `CARD` | Tarjeta |
| | | `TRANSFER` | Transferencia |
| | | `MIXED` | Mixto |
| | | `MERCADOPAGO` | MercadoPago |
| Estado de suscripción | `SubscriptionStatus` | `ACTIVE`, `PAST_DUE`, `CANCELED`, `TRIALING` | |

Las etiquetas en español viven en un solo lugar del frontend, `src/lib/types.ts`, en cuatro mapas: `ORDER_STATUS_LABELS`, `KITCHEN_STATUS_LABELS`, `TABLE_STATUS_LABELS` y `PAYMENT_METHOD_LABELS`. Es el único punto donde los identificadores en inglés y la copia en español se cruzan, y conviene que siga siendo así.

## Roles

| Español | `UserRole` | Alcance |
|---|---|---|
| Super administrador | `SUPER_ADMIN` | Plataforma: planes y suscripciones. Pertenece al inquilino técnico `platform` |
| Administrador | `ADMIN` | Todo dentro de su restaurante |
| Mesero | `WAITER` | Mesas y comandas |
| Cocina | `KITCHEN` | Tablero de cocina |
| Cajero | `CASHIER` | Cobro y tickets |

En el backend `ADMIN` aparece en todos los `@PreAuthorize` del camino operativo, así que puede hacer el recorrido completo. No existe todavía endpoint para crear usuarios con los otros roles.

## Campos y conceptos recurrentes

| Español | Inglés |
|---|---|
| folio | `folio` (se conserva; es un término contable mexicano sin equivalente breve) |
| propina | `tip` |
| porcentaje de propina | `tipPercent` |
| comensales | `guestCount` |
| subtotal | `subtotal` |
| total | `total` |
| mesero asignado | `waiterId` |
| cajero | `cashierId` |
| fecha de apertura | `openedAt` |
| enviada a cocina | `sentToKitchenAt` |
| fecha de cierre | `closedAt` |
| existencia | `stock` |
| existencia mínima | `minStock` |
| insumo | `inventoryItem` |
| movimiento de inventario | `inventoryMovement` |

Los prefijos de folio se conservan del sistema original: `ORD-` para comandas y `TCK-` para tickets, seguidos de la marca de tiempo en formato `yyMMddHHmmss` UTC.

## Correspondencia con el sistema Flask

| Flask / MongoDB | Java / PostgreSQL |
|---|---|
| `utils/tenant_context.py` (contextvars) | `com.callejon9.tenancy.TenantContext` (ThreadLocal) |
| `NoTenantContextError` | `NoTenantContextException` |
| `models/base_model.py` inyectando el filtro | Políticas RLS en las 14 tablas |
| `models/comanda_model.py` | `com.callejon9.order` |
| `models/venta_model.py` | `com.callejon9.sale` |
| `models/mesa_model.py` | `com.callejon9.table` |
| `models/ticket_model.py` | `com.callejon9.ticket` |
| `models/empleado_model.py` | `com.callejon9.user` |
| `models/restaurante_model.py` | `com.callejon9.platform.tenant` |
| `bcrypt` (Python) | `BCryptPasswordEncoder` — **los hashes existentes siguen siendo válidos** |
| `PyJWT` | `io.jsonwebtoken` (jjwt) |
| `pyotp` + `qrcode` | `dev.samstevens.totp` |
| `Flask-SocketIO` | WebSocket + STOMP de Spring |
| `fpdf` | OpenPDF |
| `Flask-Session` | JWT en cookie `httpOnly` + tabla `refresh_tokens` |
| `flask-limiter` | pendiente (Bucket4j en el diseño) |

## Tres defectos del modelado original que la migración corrige

**`mesas.numero` tenía tipos mezclados.** Convivían enteros y cadenas en la misma colección, y `Mesa.find_by_numero` hacía `{"numero": {"$in": [int(n), str(n)]}}` para sobrevivirlo. Ahora es una columna `integer` con `UNIQUE (tenant_id, number)`.

**`comandas` apuntaba a `mesa_numero`, no a un identificador.** Sin integridad referencial: renumerar una mesa dejaba huérfano su histórico. Ahora es una llave foránea.

**`productos` y `platillos` eran dos colecciones para el mismo concepto.** Se unificaron en `products`, con `category_id` normalizado a la tabla `categories`.
