import { FlatList, Linking, Pressable, Share, StyleSheet, Text, View } from 'react-native';
import { EVENT_TYPE_LABEL, STATUS_COPY } from '../notifications/messages';
import type { InboxItem, PushRegistration } from '../notifications/push';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

interface Props {
  registration: PushRegistration | null;
  registering: boolean;
  inbox: InboxItem[];
  selected: InboxItem | null;
  onRetry: () => void;
  onSelect: (item: InboxItem) => void;
  onBack: () => void;
}

/** Muestra el token solo en desarrollo; en una build de producción lo enmascara. */
function displayToken(token: string): string {
  if (__DEV__) return token;
  return `${token.slice(0, 22)}…${token.slice(-4)}`;
}

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'medium' });
}

/** Parte 2 — permisos, token, recepción y detalle de la notificación tocada. */
export function NotificationsScreen({ registration, registering, inbox, selected, onRetry, onSelect, onBack }: Props) {
  return (
    <FlatList
      data={inbox}
      keyExtractor={(item) => `${item.id}-${item.origin}`}
      contentContainerStyle={styles.container}
      ListHeaderComponent={
        <View style={styles.header}>
          <View style={styles.titleRow}>
            <Text style={styles.title}>Notificaciones</Text>
            <Pressable accessibilityRole="button" onPress={onBack} hitSlop={8} style={styles.back}>
              <Text style={styles.backLabel}>Volver</Text>
            </Pressable>
          </View>

          <PermissionCard registration={registration} registering={registering} onRetry={onRetry} />

          {selected ? (
            <View style={styles.card}>
              <Text style={styles.cardTitle}>Notificación seleccionada</Text>
              <Detail label="Título" value={selected.title} />
              <Detail label="Mensaje" value={selected.body} />
              <Detail label="Tipo de evento" value={EVENT_TYPE_LABEL[selected.data.eventType]} />
              <Detail label="Estado" value={STATUS_COPY[selected.data.status].label} />
              <Detail label="Identificador" value={selected.data.requestId} />
              <Detail label="Recibida" value={formatTime(selected.receivedAt)} />
            </View>
          ) : null}

          <Text style={styles.section}>Recibidas en esta sesión ({inbox.length})</Text>
        </View>
      }
      ListEmptyComponent={
        <Text style={styles.meta}>
          {registration?.status === 'granted'
            ? 'Aún no llega ninguna. Envía una con pnpm push:send usando el token de arriba.'
            : 'Aún no llega ninguna. Las notificaciones aparecerán aquí cuando el dispositivo quede registrado.'}
        </Text>
      }
      renderItem={({ item }) => (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${item.title}. ${item.body}. ${item.data.requestId}`}
          onPress={() => onSelect(item)}
          style={({ pressed }) => [styles.item, selected?.id === item.id && styles.itemSelected, pressed && styles.pressed]}
        >
          <View style={styles.itemRow}>
            <Text style={styles.itemTitle}>{item.title}</Text>
            <Text style={styles.badge}>{STATUS_COPY[item.data.status].label}</Text>
          </View>
          <Text style={styles.itemBody}>{item.body}</Text>
          <Text style={styles.meta}>
            {item.data.requestId} · {item.origin === 'opened' ? 'abierta desde el sistema' : 'con la app abierta'} ·{' '}
            {formatTime(item.receivedAt)}
          </Text>
        </Pressable>
      )}
    />
  );
}

function PermissionCard({ registration, registering, onRetry }: Pick<Props, 'registration' | 'registering' | 'onRetry'>) {
  if (registering || !registration) {
    return (
      <Banner tone="info" title="Registrando el dispositivo">
        Solicitando permiso y token al servicio de Expo…
      </Banner>
    );
  }

  if (registration.status === 'denied') {
    return (
      <View style={styles.block}>
        <Banner tone="error" title="Permiso de notificaciones denegado">
          La app no mostrará avisos de solicitudes. Los cambios de estado seguirán disponibles al consultar la app
          manualmente.
        </Banner>
        {registration.canAskAgain ? (
          <Button label="Volver a pedir permiso" variant="secondary" onPress={onRetry} />
        ) : (
          <Button label="Abrir ajustes del sistema" variant="secondary" onPress={() => void Linking.openSettings()} />
        )}
      </View>
    );
  }

  if (registration.status === 'unavailable') {
    return (
      <View style={styles.block}>
        <Banner tone="info" title="Push remoto no disponible en este entorno">
          {registration.reason}
        </Banner>
        <Button label="Reintentar" variant="secondary" onPress={onRetry} />
      </View>
    );
  }

  return (
    <View style={styles.block}>
      <Banner tone="success" title="Dispositivo registrado">
        Permiso concedido. Este es el token push con el que el servicio remoto identifica este teléfono.
      </Banner>
      <Text selectable style={styles.token} accessibilityLabel="Token push del dispositivo">
        {displayToken(registration.token)}
      </Text>
      {__DEV__ ? (
        <Button
          label="Compartir token"
          variant="secondary"
          onPress={() => void Share.share({ message: registration.token })}
        />
      ) : null}
    </View>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detail}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 10, paddingBottom: 32 },
  header: { gap: 12 },
  titleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontSize: 26, fontWeight: '800', color: colors.foreground },
  back: { paddingVertical: 8, paddingHorizontal: 12, borderRadius: radius, borderWidth: 1, borderColor: colors.border },
  backLabel: { fontSize: 14, fontWeight: '600', color: colors.foreground },
  block: { gap: 8 },
  token: {
    fontFamily: 'monospace',
    fontSize: 13,
    color: colors.foreground,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius,
    padding: 10,
  },
  card: { backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: radius, padding: 12, gap: 6 },
  cardTitle: { fontSize: 16, fontWeight: '700', color: colors.foreground },
  detail: { flexDirection: 'row', gap: 8 },
  detailLabel: { width: 110, fontSize: 14, color: colors.mutedForeground },
  detailValue: { flex: 1, fontSize: 14, fontWeight: '600', color: colors.foreground },
  section: { fontSize: 15, fontWeight: '700', color: colors.foreground, marginTop: 4 },
  meta: { fontSize: 13, color: colors.mutedForeground },
  item: { backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: radius, padding: 12, gap: 4 },
  itemSelected: { borderColor: colors.foreground },
  pressed: { opacity: 0.85 },
  itemRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  itemTitle: { flex: 1, fontSize: 15, fontWeight: '700', color: colors.foreground },
  itemBody: { fontSize: 14, color: colors.foreground },
  badge: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.success,
    backgroundColor: colors.successSoft,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 2,
    overflow: 'hidden',
  },
});
