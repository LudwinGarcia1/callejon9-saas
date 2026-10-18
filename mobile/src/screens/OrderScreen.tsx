import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { ApiError } from '../api/client';
import type { Order, OrderStatus, Product, RestaurantTable } from '../api/types';
import { addOrderItems, cancelOrder, fetchOrder, fetchOrders, fetchProducts, openOrder } from '../api/waiter';
import { Banner, Button } from '../ui/components';
import { colors, formatMoney, radius } from '../ui/theme';

const MAX_GUESTS = 50;
const MAX_QUANTITY = 99;
/** Estados en los que una orden sigue abierta y admite más productos. */
const OPEN_STATUSES: OrderStatus[] = ['NEW', 'SENT', 'READY'];

interface Props {
  table: RestaurantTable;
  onBack: () => void;
  /** `added` indica que se agregaron productos a una orden que ya existía. */
  onSubmitted: (order: Order, added: boolean) => void;
}

/**
 * Parte 2 — envío de datos. Consulta el menú (GET /products) y envía el
 * pedido. Sobre una mesa libre abre la orden y le agrega los productos; sobre
 * una mesa ocupada carga su orden abierta y solo agrega lo nuevo.
 */
export function OrderScreen({ table, onBack, onSubmitted }: Props) {
  const occupied = table.status === 'OCCUPIED';
  const [products, setProducts] = useState<Product[] | null>(null);
  const [existing, setExisting] = useState<Order | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [guests, setGuests] = useState(Math.min(Math.max(table.capacity, 1), MAX_GUESTS));
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const [productData, order] = await Promise.all([
        fetchProducts(),
        occupied ? findOpenOrder(table.id) : Promise.resolve(null),
      ]);
      setProducts(productData.filter((product) => product.active));
      setExisting(order);
    } catch (cause) {
      setLoadError(cause instanceof ApiError ? cause.message : 'No se pudo cargar la información de la mesa.');
    }
  }, [occupied, table.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = useMemo(
    () => (products ?? []).filter((product) => (quantities[product.id] ?? 0) > 0),
    [products, quantities],
  );
  const addedTotal = selected.reduce((sum, product) => sum + product.price * quantities[product.id], 0);
  const itemCount = selected.reduce((sum, product) => sum + quantities[product.id], 0);
  const ready = products !== null && (!occupied || existing !== null);

  function changeQuantity(productId: string, delta: number) {
    setQuantities((current) => {
      const next = Math.min(MAX_QUANTITY, Math.max(0, (current[productId] ?? 0) + delta));
      return { ...current, [productId]: next };
    });
  }

  async function handleSubmit() {
    if (selected.length === 0) {
      setSubmitError('Agrega al menos un producto al pedido.');
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    const items = selected.map((product) => ({ productId: product.id, quantity: quantities[product.id] }));

    if (existing) {
      try {
        onSubmitted(await addOrderItems(existing.id, items), true);
      } catch (cause) {
        setSubmitError(describe(cause, 'No se pudieron agregar los productos.'));
        setSubmitting(false);
      }
      return;
    }

    let opened: Order;
    try {
      opened = await openOrder({ tableId: table.id, guestCount: guests });
    } catch (cause) {
      setSubmitError(describe(cause, 'No se pudo abrir la orden.'));
      setSubmitting(false);
      return;
    }

    try {
      onSubmitted(await addOrderItems(opened.id, items), false);
    } catch (cause) {
      // La orden ya existe pero quedó vacía: se cancela para liberar la mesa.
      await cancelOrder(opened.id).catch(() => undefined);
      setSubmitError(describe(cause, 'No se pudieron registrar los productos.'));
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" onPress={onBack} hitSlop={8} disabled={submitting}>
          <Text style={styles.back}>‹ Mesas</Text>
        </Pressable>
        <Text style={styles.title}>Mesa {table.number}</Text>
      </View>

      {existing ? (
        <View style={styles.existing}>
          <View style={styles.summary}>
            <Text style={styles.guestsLabel}>Orden {existing.folio}</Text>
            <Text style={styles.meta}>{existing.guestCount} comensales</Text>
          </View>
          {existing.items.map((item) => (
            <View key={item.id} style={styles.summary}>
              <Text style={[styles.meta, styles.flex]}>
                {item.quantity} × {item.productName}
              </Text>
              <Text style={styles.meta}>{formatMoney(item.unitPrice * item.quantity)}</Text>
            </View>
          ))}
          <View style={styles.summary}>
            <Text style={styles.guestsLabel}>Cuenta actual</Text>
            <Text style={styles.guestsLabel}>{formatMoney(existing.total)}</Text>
          </View>
          {existing.status !== 'NEW' ? (
            <Text style={styles.meta}>Ya está en cocina: lo que agregues aparecerá ahí como pendiente.</Text>
          ) : null}
        </View>
      ) : occupied ? null : (
        <View style={styles.guests}>
          <Text style={styles.guestsLabel}>Comensales</Text>
          <Stepper
            value={guests}
            onDecrement={() => setGuests((value) => Math.max(1, value - 1))}
            onIncrement={() => setGuests((value) => Math.min(MAX_GUESTS, value + 1))}
            disabled={submitting}
            label="comensales"
          />
        </View>
      )}

      {loadError ? (
        <View style={styles.gap}>
          <Banner tone="error" title="Error al consultar la mesa">
            {loadError}
          </Banner>
          <Button label="Reintentar" variant="secondary" onPress={() => void load()} />
        </View>
      ) : !ready ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.foreground} />
        </View>
      ) : (
        <FlatList
          style={styles.flex}
          data={products}
          keyExtractor={(product) => product.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={existing ? <Text style={styles.meta}>Agregar más productos</Text> : null}
          ListEmptyComponent={<Text style={styles.meta}>No hay productos activos en el menú.</Text>}
          renderItem={({ item }) => (
            <View style={styles.product}>
              <View style={styles.flex}>
                <Text style={styles.productName}>{item.name}</Text>
                <Text style={styles.meta}>{formatMoney(item.price)}</Text>
              </View>
              <Stepper
                value={quantities[item.id] ?? 0}
                onDecrement={() => changeQuantity(item.id, -1)}
                onIncrement={() => changeQuantity(item.id, 1)}
                disabled={submitting}
                label={item.name}
              />
            </View>
          )}
        />
      )}

      <View style={styles.footer}>
        {submitError ? (
          <Banner tone="error" title="El pedido no se envió">
            {submitError}
          </Banner>
        ) : null}
        <View style={styles.summary}>
          <Text style={styles.meta}>
            {itemCount} productos{existing ? ' nuevos' : ''}
          </Text>
          <Text style={styles.total}>{formatMoney(addedTotal)}</Text>
        </View>
        <Button
          label={existing ? 'Agregar a la orden' : 'Enviar pedido al servidor'}
          onPress={handleSubmit}
          loading={submitting}
          disabled={!ready || itemCount === 0}
        />
      </View>
    </View>
  );
}

/** La orden abierta de una mesa ocupada: GET /orders y luego GET /orders/{id}. */
async function findOpenOrder(tableId: string): Promise<Order> {
  const summary = (await fetchOrders()).find(
    (order) => order.tableId === tableId && OPEN_STATUSES.includes(order.status),
  );
  if (!summary) {
    throw new ApiError('notFound', 'La mesa está ocupada, pero no tiene una orden abierta.');
  }
  try {
    return await fetchOrder(summary.id);
  } catch (cause) {
    // El backend solo deja a cada mesero tocar sus propias órdenes.
    if (cause instanceof ApiError && cause.kind === 'forbidden') {
      throw new ApiError('forbidden', 'Esta mesa la atiende otro mesero. Solo él o un administrador pueden agregar productos.', 403);
    }
    throw cause;
  }
}

function describe(cause: unknown, fallback: string): string {
  if (!(cause instanceof ApiError)) return fallback;
  const details = Object.values(cause.fieldErrors);
  return details.length > 0 ? `${cause.message} ${details.join(' ')}` : cause.message;
}

interface StepperProps {
  value: number;
  onDecrement: () => void;
  onIncrement: () => void;
  disabled: boolean;
  label: string;
}

function Stepper({ value, onDecrement, onIncrement, disabled, label }: StepperProps) {
  return (
    <View style={styles.stepper}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Quitar ${label}`}
        onPress={onDecrement}
        disabled={disabled}
        style={styles.stepperButton}
      >
        <Text style={styles.stepperSign}>−</Text>
      </Pressable>
      <Text style={styles.stepperValue}>{value}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Agregar ${label}`}
        onPress={onIncrement}
        disabled={disabled}
        style={styles.stepperButton}
      >
        <Text style={styles.stepperSign}>+</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { flex: 1, padding: 16, gap: 12 },
  header: { gap: 4 },
  back: { fontSize: 16, fontWeight: '600', color: colors.mutedForeground },
  title: { fontSize: 26, fontWeight: '800', color: colors.foreground },
  meta: { fontSize: 14, color: colors.mutedForeground },
  gap: { gap: 12 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  guests: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.card,
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12,
  },
  existing: {
    backgroundColor: colors.card,
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12,
    gap: 6,
  },
  guestsLabel: { fontSize: 15, fontWeight: '600', color: colors.foreground },
  list: { gap: 8, paddingBottom: 12 },
  product: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.card,
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 12,
  },
  productName: { fontSize: 15, fontWeight: '600', color: colors.foreground },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  stepperButton: {
    width: 40,
    height: 40,
    borderRadius: radius,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  stepperSign: { fontSize: 20, fontWeight: '600', color: colors.foreground },
  stepperValue: { minWidth: 28, textAlign: 'center', fontSize: 16, fontWeight: '700', color: colors.foreground },
  footer: { gap: 10, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 12 },
  summary: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 12 },
  total: { fontSize: 22, fontWeight: '800', color: colors.foreground },
});
