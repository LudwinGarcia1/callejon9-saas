import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { ApiError } from '../api/client';
import type { RestaurantTable, TableStatus } from '../api/types';
import { fetchTables } from '../api/waiter';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

const STATUS_LABEL: Record<TableStatus, string> = {
  FREE: 'Libre',
  OCCUPIED: 'Ocupada',
  RESERVED: 'Reservada',
  CLEANING: 'En limpieza',
};

interface Props {
  userName: string;
  restaurantName: string | null;
  welcome: boolean;
  onSelectTable: (table: RestaurantTable) => void;
  onLogout: () => void;
}

/** Parte 1 — consumo de datos: GET /api/v1/tables y despliegue en lista. */
export function TablesScreen({ userName, restaurantName, welcome, onSelectTable, onLogout }: Props) {
  const [tables, setTables] = useState<RestaurantTable[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (mode: 'initial' | 'refresh') => {
    if (mode === 'refresh') setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const data = await fetchTables();
      setTables([...data].sort((a, b) => a.number - b.number));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'No se pudieron cargar las mesas.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load('initial');
  }, [load]);

  const freeCount = tables?.filter((table) => table.status === 'FREE').length ?? 0;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <Text style={styles.title}>Mesas</Text>
          <Text style={styles.meta} numberOfLines={1}>
            {userName}
            {restaurantName ? ` · ${restaurantName}` : ''}
          </Text>
        </View>
        <Pressable accessibilityRole="button" onPress={onLogout} hitSlop={8} style={styles.logout}>
          <Text style={styles.logoutLabel}>Salir</Text>
        </Pressable>
      </View>

      {welcome ? (
        <Banner tone="success" title="Autenticación exitosa">
          Sesión iniciada como {userName}. La sesión se guarda en una cookie segura del sistema.
        </Banner>
      ) : null}

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.foreground} />
          <Text style={styles.meta}>Consultando mesas en el servidor…</Text>
        </View>
      ) : error ? (
        <View style={styles.errorBox}>
          <Banner tone="error" title="Error al consultar las mesas">
            {error}
          </Banner>
          <Button label="Reintentar" variant="secondary" onPress={() => void load('initial')} />
        </View>
      ) : (
        <FlatList
          data={tables ?? []}
          keyExtractor={(table) => table.id}
          numColumns={2}
          columnWrapperStyle={styles.row}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load('refresh')} />}
          ListHeaderComponent={
            <Text style={styles.meta}>
              {tables?.length ?? 0} mesas · {freeCount} libres · toca una mesa libre para tomar el pedido
            </Text>
          }
          ListEmptyComponent={<Text style={styles.meta}>El restaurante no tiene mesas activas.</Text>}
          renderItem={({ item }) => <TableCard table={item} onPress={() => onSelectTable(item)} />}
        />
      )}
    </View>
  );
}

function TableCard({ table, onPress }: { table: RestaurantTable; onPress: () => void }) {
  const free = table.status === 'FREE';
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !free }}
      accessibilityLabel={`Mesa ${table.number}, ${STATUS_LABEL[table.status]}`}
      disabled={!free}
      onPress={onPress}
      style={({ pressed }) => [styles.card, !free && styles.cardBusy, pressed && styles.cardPressed]}
    >
      <Text style={styles.cardNumber}>{table.number}</Text>
      <Text style={styles.cardMeta}>{table.capacity} personas</Text>
      <View style={[styles.badge, free ? styles.badgeFree : styles.badgeBusy]}>
        <Text style={[styles.badgeText, { color: free ? colors.success : colors.mutedForeground }]}>
          {STATUS_LABEL[table.status]}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 12 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  headerText: { flex: 1 },
  title: { fontSize: 26, fontWeight: '800', color: colors.foreground },
  meta: { fontSize: 14, color: colors.mutedForeground },
  logout: { paddingVertical: 8, paddingHorizontal: 12, borderRadius: radius, borderWidth: 1, borderColor: colors.border },
  logoutLabel: { fontSize: 14, fontWeight: '600', color: colors.foreground },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  errorBox: { gap: 12 },
  list: { gap: 12, paddingBottom: 24 },
  row: { gap: 12 },
  card: {
    flex: 1,
    backgroundColor: colors.card,
    borderRadius: radius * 1.4,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    gap: 4,
  },
  cardBusy: { backgroundColor: colors.muted },
  cardPressed: { opacity: 0.8 },
  cardNumber: { fontSize: 28, fontWeight: '800', color: colors.foreground },
  cardMeta: { fontSize: 13, color: colors.mutedForeground },
  badge: { alignSelf: 'flex-start', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 3, marginTop: 6 },
  badgeFree: { backgroundColor: colors.successSoft },
  badgeBusy: { backgroundColor: colors.background },
  badgeText: { fontSize: 12, fontWeight: '700' },
});
