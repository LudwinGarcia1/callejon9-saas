import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { cartTotal, MAX_NOTES_LENGTH, type Cart } from '../domain/cart';
import type { SentRecord } from '../offline/orderHistory';
import type { PendingOrder } from '../offline/outbox';
import { Banner, Button } from '../ui/components';
import { Stepper } from '../ui/Stepper';
import { colors, formatMoney, formatShortDate, radius } from '../ui/theme';
import type { Notice } from './MenuScreen';

interface CartScreenProps {
  cart: Cart;
  pending: PendingOrder[];
  history: SentRecord[];
  online: boolean;
  sending: boolean;
  busyPendingId: string | null;
  notice: Notice | null;
  onSetQuantity: (productId: string, quantity: number) => void;
  onSetNotes: (productId: string, notes: string) => void;
  onSend: () => void;
  onBack: () => void;
  onSendPending: (localId: string) => void;
  onDiscardPending: (localId: string) => void;
}

const STATUS_LABEL: Record<PendingOrder['status'], string> = {
  pending: 'Esperando conexión',
  unconfirmed: 'Sin confirmar',
  expired: 'Vencido',
  rejected: 'Rechazado',
};

export function CartScreen(props: CartScreenProps) {
  const { cart, pending, history, online } = props;

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Mi pedido</Text>
        {props.notice ? <Banner tone={props.notice.tone}>{props.notice.text}</Banner> : null}

        {cart.length === 0 ? (
          <Text style={styles.muted}>{history.length ? 'No tienes productos nuevos por pedir.' : 'Aún no agregas productos.'}</Text>
        ) : null}
        {cart.map((line) => (
          <View key={line.productId} style={styles.card}>
            <View style={styles.row}>
              <View style={styles.flex}>
                <Text style={styles.name}>{line.name}</Text>
                <Text style={styles.muted}>{formatMoney(line.unitPrice)} c/u · {formatMoney(line.unitPrice * line.quantity)}</Text>
              </View>
              <Stepper value={line.quantity} label={line.name} onChange={(q) => props.onSetQuantity(line.productId, q)} />
            </View>
            <TextInput
              value={line.notes}
              onChangeText={(text) => props.onSetNotes(line.productId, text)}
              placeholder="Notas para cocina (opcional)"
              placeholderTextColor={colors.mutedForeground}
              maxLength={MAX_NOTES_LENGTH}
              style={styles.notes}
            />
          </View>
        ))}

        {cart.length > 0 ? (
          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>Total estimado</Text>
            <Text style={styles.totalValue}>{formatMoney(cartTotal(cart))}</Text>
          </View>
        ) : null}

        {cart.length > 0 && !online ? (
          <Banner tone="info">
            No tienes conexión. Tu pedido se guardará en el teléfono y se enviará a cocina en cuanto vuelva la señal (si
            pasan más de 30 minutos, te pediremos confirmarlo).
          </Banner>
        ) : null}

        {pending.length > 0 ? (
          <View style={styles.pending}>
            <Text style={styles.subtitle}>Pedidos por enviar</Text>
            {pending.map((order) => (
              <View key={order.localId} style={styles.card}>
                <View style={styles.row}>
                  <Text style={[styles.name, styles.flex]}>Mesa {order.tableNumber} · {formatShortDate(order.createdAt)}</Text>
                  <Text style={styles.status}>{STATUS_LABEL[order.status]}</Text>
                </View>
                <Text style={styles.muted}>{order.summary}</Text>
                <Text style={styles.muted}>Total estimado {formatMoney(order.estimatedTotal)}</Text>
                {order.message ? <Text style={styles.message}>{order.message}</Text> : null}
                {order.status !== 'pending' ? (
                  <View style={styles.row}>
                    <View style={styles.flex}>
                      <Button label="Descartar" variant="secondary" onPress={() => props.onDiscardPending(order.localId)} />
                    </View>
                    <View style={styles.flex}>
                      <Button
                        label="Enviar ahora"
                        onPress={() => props.onSendPending(order.localId)}
                        disabled={!online || order.status === 'rejected'}
                        loading={props.busyPendingId === order.localId}
                      />
                    </View>
                  </View>
                ) : (
                  <Button label="Cancelar este pedido" variant="secondary" onPress={() => props.onDiscardPending(order.localId)} />
                )}
              </View>
            ))}
          </View>
        ) : null}
        {history.length > 0 ? (
          <View style={styles.pending}>
            <Text style={styles.subtitle}>Enviados a cocina</Text>
            <Text style={styles.muted}>Guardados en tu teléfono: los puedes ver aunque no tengas conexión.</Text>
            {history.map((record) => {
              const total = record.lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0);
              return (
                <View key={record.localId} style={styles.card}>
                  <View style={styles.row}>
                    <Text style={[styles.name, styles.flex]}>Folio {record.folio}</Text>
                    <Text style={styles.sent}>Enviado {formatShortDate(record.sentAt)}</Text>
                  </View>
                  {record.lines.map((line, index) => (
                    <View key={index} style={styles.lineRow}>
                      <Text style={[styles.lineText, styles.flex]}>
                        {line.quantity} × {line.name}
                        {line.notes ? <Text style={styles.muted}>{`\n${line.notes}`}</Text> : null}
                      </Text>
                      <Text style={styles.lineText}>{formatMoney(line.unitPrice * line.quantity)}</Text>
                    </View>
                  ))}
                  <View style={styles.totalRow}>
                    <Text style={styles.muted}>Total estimado</Text>
                    <Text style={styles.name}>{formatMoney(total)}</Text>
                  </View>
                </View>
              );
            })}
            <Text style={styles.muted}>La cuenta final la entrega el mesero con los precios vigentes.</Text>
          </View>
        ) : null}
      </ScrollView>

      <View style={styles.footer}>
        <View style={styles.flex}>
          <Button label="Seguir viendo" variant="secondary" onPress={props.onBack} />
        </View>
        <View style={styles.flex}>
          <Button
            label={online ? 'Enviar a cocina' : 'Guardar y enviar luego'}
            onPress={props.onSend}
            disabled={cart.length === 0}
            loading={props.sending}
          />
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 16, gap: 12 },
  title: { fontSize: 24, fontWeight: '800', color: colors.foreground },
  subtitle: { fontSize: 18, fontWeight: '800', color: colors.foreground },
  card: { backgroundColor: colors.card, borderRadius: radius, borderWidth: 1, borderColor: colors.border, padding: 12, gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  name: { fontSize: 16, fontWeight: '700', color: colors.foreground },
  muted: { color: colors.mutedForeground, fontSize: 14 },
  notes: { borderWidth: 1, borderColor: colors.border, borderRadius: radius, paddingHorizontal: 10, minHeight: 40, color: colors.foreground },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  totalLabel: { fontSize: 16, color: colors.foreground },
  totalValue: { fontSize: 18, fontWeight: '800', color: colors.foreground },
  pending: { gap: 10, marginTop: 8 },
  status: { fontSize: 12, fontWeight: '700', color: colors.warning },
  message: { color: colors.destructive, fontSize: 13 },
  sent: { fontSize: 12, fontWeight: '700', color: colors.success },
  lineRow: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  lineText: { fontSize: 14, color: colors.foreground },
  footer: { flexDirection: 'row', gap: 10, padding: 12, borderTopWidth: 1, borderColor: colors.border },
});
