import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { ApiError } from '../api/client';
import type { Order, OrderStatus } from '../api/types';
import { sendToKitchen } from '../api/waiter';
import { Banner, Button } from '../ui/components';
import { colors, formatMoney, radius } from '../ui/theme';

const STATUS_LABEL: Record<OrderStatus, string> = {
  NEW: 'Nueva (sin enviar a cocina)',
  SENT: 'Enviada a cocina',
  READY: 'Lista',
  PAID: 'Pagada',
  CANCELED: 'Cancelada',
};

/** Resultado de la operación: muestra lo que respondió el servidor. */
export function ResultScreen({ order: initial, onDone }: { order: Order; onDone: () => void }) {
  const [order, setOrder] = useState(initial);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSendToKitchen() {
    setSending(true);
    setError(null);
    try {
      setOrder(await sendToKitchen(order.id));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'No se pudo enviar a cocina.');
    } finally {
      setSending(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Banner tone="success" title={order.status === 'SENT' ? 'Pedido enviado a cocina' : 'Pedido registrado'}>
        El servidor respondió con el folio {order.folio}.
      </Banner>

      <View style={styles.card}>
        <Row label="Folio" value={order.folio} />
        <Row label="Estado" value={STATUS_LABEL[order.status]} />
        <Row label="Comensales" value={String(order.guestCount)} />
        <Row label="Abierta" value={new Date(order.openedAt).toLocaleString('es-MX')} />
        <View style={styles.divider} />
        {order.items.map((item) => (
          <Row
            key={item.id}
            label={`${item.quantity} × ${item.productName}`}
            value={formatMoney(item.unitPrice * item.quantity)}
          />
        ))}
        <View style={styles.divider} />
        <Row label="Total" value={formatMoney(order.total)} strong />
      </View>

      {error ? <Banner tone="error" title="Error">{error}</Banner> : null}
      {order.status === 'NEW' ? (
        <Button label="Enviar a cocina" onPress={handleSendToKitchen} loading={sending} />
      ) : null}
      <Button label="Volver a mesas" variant="secondary" onPress={onDone} disabled={sending} />
    </ScrollView>
  );
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <View style={styles.row}>
      <Text style={[styles.label, strong && styles.strong]}>{label}</Text>
      <Text style={[styles.value, strong && styles.strong]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  card: {
    backgroundColor: colors.card,
    borderRadius: radius * 1.4,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    gap: 8,
  },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  label: { flex: 1, fontSize: 15, color: colors.mutedForeground },
  value: { fontSize: 15, color: colors.foreground, fontWeight: '600', textAlign: 'right' },
  strong: { fontSize: 18, fontWeight: '800', color: colors.foreground },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: 4 },
});
