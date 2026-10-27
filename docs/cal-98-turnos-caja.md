# CAL-98: turnos de caja y arqueo

Cada cajero abre un turno con su fondo inicial antes de cobrar. PostgreSQL limita a uno los turnos abiertos por cajero y restaurante. El checkout toma un bloqueo de la sesión y guarda su identificador en la venta dentro de la misma transacción; si no hay turno abierto, responde `422` sin crear venta, pago ni ticket.

## API

- `GET /api/v1/cash-sessions`: ADMIN ve los turnos del restaurante; CASHIER ve los suyos.
- `GET /api/v1/cash-sessions/{id}`: detalle, movimientos y resumen por método de pago.
- `POST /api/v1/cash-sessions` con `{"openingFund": 1000}`: abre un turno para el usuario autenticado.
- `POST /api/v1/cash-sessions/{id}/movements` con `{"type":"WITHDRAWAL","amount":200,"reason":"Compra urgente"}`: registra depósito o retiro con motivo obligatorio.
- `POST /api/v1/cash-sessions/{id}/close` con `{"countedCash": 1480}`: el cajero propietario guarda el arqueo.
- `POST /api/v1/cash-sessions/{id}/force-close` con monto contado y motivo: ADMIN cierra un turno abandonado.

No se acepta un `cashier_id` ni `tenant_id` del navegador. El turno y el restaurante se derivan del principal autenticado y del `TenantContext` fijado por la cookie firmada.

## Cálculo y bitácora

El efectivo esperado es fondo inicial + efectivo recibido − cambio + depósitos − retiros. En el modelo de pagos actual, `received_amount - amount` es el cambio y `amount` es el efectivo aplicado a la cuenta. Los pagos que no son en efectivo se muestran en el resumen imprimible, pero no alteran el cajón.

Ejemplo: fondo `$1,000` + venta en efectivo de `$2,500` − retiro de `$2,000` = esperado `$1,500`. Si se cuentan `$1,480`, la diferencia guardada es `-$20`. Un retiro superior al efectivo esperado se rechaza. No se editan movimientos; una sesión cerrada conserva importes, hora, usuario y motivo del cierre. Un trigger de PostgreSQL impide modificar sus valores después del primer cierre.

## Aislamiento y persistencia

El script manual `scripts/verify-rls.sql` comprueba la visibilidad de turnos y movimientos entre dos restaurantes, el bloqueo de inserciones por `WITH CHECK` y las referencias cruzadas rechazadas por las claves compuestas. `CheckoutControllerTest` cubre además dos aperturas concurrentes del mismo cajero y confirma que queda un solo turno abierto.

Las migraciones append-only V13 y V14 crean `cash_sessions` y `cash_movements` con `ENABLE` y `FORCE ROW LEVEL SECURITY`, la política de tenant null-safe y claves foráneas compuestas. Estas llaves también impiden asociar una sesión a un cajero de otro restaurante o un movimiento a una sesión extranjera. `sales.cash_session_id` queda vinculado al mismo tenant y cajero.

Las 17 tablas de datos por restaurante quedan clasificadas en `DatabaseSecurityContractTest` y cubiertas por la comprobación del esquema. `TenantIsolationTest` verifica lectura cruzada y referencias de sesión/movimiento entre restaurantes.

## Interfaz

La sección **Turnos de caja** está disponible para ADMIN y CASHIER. Permite abrir el turno propio, consultar sesiones, registrar depósito/retiro, revisar el resumen por método y movimientos, imprimir el resumen y cerrar con arqueo. ADMIN puede forzar el cierre de sesiones ajenas con un motivo obligatorio. La autorización real se aplica en el backend.

## Verificación

`CheckoutControllerTest` cubre el requisito de turno abierto, rechazo de un segundo turno, el ejemplo de arqueo y la inmutabilidad. `CashDrawerCalculatorTest` verifica la fórmula, y `TenantIsolationTest` cubre el aislamiento y las referencias compuestas. La ejecución pendiente de las puertas debe quedar registrada en `workflow/state/review-history.jsonl`; un resultado local no sustituye la aprobación independiente ni la integración del PR.
