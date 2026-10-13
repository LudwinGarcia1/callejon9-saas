# CAL-97: pagos divididos y mixtos

El cobro registra una venta y uno o más pagos manuales en una sola transacción, junto con el ticket, el cierre de la orden y la liberación de la mesa. La orden se bloquea mientras se cobra para impedir dos ventas por cobros simultáneos. Las modificaciones de orden y cocina comparten ese bloqueo.

## Contrato

`POST /api/v1/orders/{id}/checkout` acepta:

```json
{
  "tipPercent": 0,
  "payments": [
    { "method": "CASH", "amount": 300 },
    { "method": "CARD", "amount": 200 }
  ]
}
```

`amount` en la petición es el monto recibido. Cada pago admite un método concreto (`CASH`, `CARD`, `TRANSFER` o `MERCADOPAGO`), un monto positivo con hasta dos decimales y máximo 99,999,999.99. Se permiten entre uno y 50 pagos. El total incluye propina. Los pagos insuficientes o el exceso de pagos sin efectivo reciben 422; no se persiste ninguna parte del cobro. Los errores de validación estructural reciben 400 mediante el contrato RFC 7807 existente.

Por compatibilidad se conserva `{ "paymentMethod": "CASH", "tipPercent": 0 }`, que genera un pago exacto. No se admite enviar ambos formatos ni utilizar `MIXED` como método de un pago individual. La venta usa el método de su único pago o `MIXED` cuando hay varios, aunque compartan método.

La respuesta del ticket agrega `payments` (método, `amount` aplicado, `receivedAmount` recibido y `change`) y `change` total. Para una cuenta de 500 con efectivo de 600, el pago aplicado es 500 y el cambio 100. Los pagos sin efectivo se aplican completos; el efectivo cubre el saldo restante siguiendo el orden de las filas. Una fila de efectivo puede quedar aplicada en cero si las anteriores ya cubrieron el saldo: conserva el recibido y su devolución completa.

El ticket guarda una fotografía inmutable del desglose, usada también en el PDF. El historial y la analítica incluyen `paymentMix`: cada método suma montos aplicados y cuenta filas de pago; el cambio no es ingreso. El resumen del historial sigue contando ventas, evitando duplicar ingresos por una cuenta dividida.

## Interfaz

Caja permite pago único exacto, división entre 1 y 50 personas con distribución del residuo en centavos, y montos libres con métodos diferentes. Muestra faltante y cambio; bloquea el cobro inválido y los controles durante el envío. Cambiar la propina requiere ajustar los montos. El resultado visible usa el ticket del servidor.

## Migración y aislamiento

`V10__split_payments.sql` agrega monto recibido, fotografía de pagos y cambio. Completa los pagos de ventas históricas cobradas que no tenían filas, y las fotografías de sus tickets, iterando cada restaurante bajo su contexto RLS. No inventa el desglose de ventas históricas `MIXED`: conserva ese método como legado. Las ventas históricas con pagos existentes conservan esas filas.

Se mantienen las 14 tablas protegidas y sus políticas `ENABLE`, `FORCE`, `USING` y `WITH CHECK`. Una clave foránea compuesta impide que un pago enlace una venta de otro restaurante, incluso si declara su propio tenant. No cambia la autoridad de la cookie JWT ni los roles de base de datos.

## Verificación

Las pruebas cubren pagos mixtos, insuficientes y excedentes; cambio, propina, precisión; rollback después de persistir los pagos; cobros concurrentes; RLS entre dos restaurantes y rechazo de vínculos cruzados; mezcla en historial/analítica; texto del PDF y estados de caja. La evidencia de las puertas ejecutadas se registra en `workflow/state/review-history.jsonl`.

`SplitPaymentMigrationTest` prepara V7 con ventas y tickets de dos restaurantes, uno con un pago existente y otro sin pagos. Actualiza hasta V10 con el propietario sin privilegios para evadir RLS y verifica el backfill, la conservación del pago previo sin duplicarlo, las fotografías y las 14 tablas con RLS forzada.

Tarjeta, transferencia y MercadoPago son registros manuales; no ejecutan cobros externos. Quedan fuera dividir por platillo y los reembolsos.
