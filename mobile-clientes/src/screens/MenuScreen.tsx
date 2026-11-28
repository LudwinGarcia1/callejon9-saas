import { useMemo, useState } from 'react';
import { Pressable, RefreshControl, SectionList, StyleSheet, Text, TextInput, View } from 'react-native';
import type { Product } from '../api/types';
import { cartCount, cartTotal, quantityOf, type Cart } from '../domain/cart';
import type { LoadedCatalog } from '../offline/catalogCache';
import { Banner, Button } from '../ui/components';
import { Stepper } from '../ui/Stepper';
import { colors, formatMoney, formatShortDate, radius } from '../ui/theme';

export interface Notice {
  tone: 'error' | 'success' | 'info';
  text: string;
}

interface MenuScreenProps {
  loaded: LoadedCatalog;
  tableNumber: number;
  cart: Cart;
  online: boolean;
  refreshing: boolean;
  callingWaiter: boolean;
  notice: Notice | null;
  pendingCount: number;
  historyCount: number;
  onSetQuantity: (product: Product, quantity: number) => void;
  onRefresh: () => void;
  onOpenCart: () => void;
  onCallWaiter: () => void;
  onChangeTable: () => void;
}

export function MenuScreen(props: MenuScreenProps) {
  const { loaded, tableNumber, cart, online, notice, pendingCount } = props;
  const [query, setQuery] = useState('');

  const sections = useMemo(() => {
    const term = query.trim().toLowerCase();
    const products = loaded.catalog.products.filter(
      (p) => p.active && (!term || p.name.toLowerCase().includes(term) || (p.description ?? '').toLowerCase().includes(term)),
    );
    const categories = [...loaded.catalog.categories].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
    const known = new Set(categories.map((c) => c.id));
    const grouped = categories
      .map((c) => ({ title: c.name, data: products.filter((p) => p.categoryId === c.id) }))
      .filter((s) => s.data.length > 0);
    const others = products.filter((p) => !p.categoryId || !known.has(p.categoryId));
    return others.length ? [...grouped, { title: 'Otros', data: others }] : grouped;
  }, [loaded, query]);

  const count = cartCount(cart);

  return (
    <View style={styles.flex}>
      <View style={styles.header}>
        <View style={styles.flex}>
          <Text style={styles.restaurant} numberOfLines={1}>{loaded.catalog.restaurantName}</Text>
          <Pressable onPress={props.onChangeTable} accessibilityRole="button" accessibilityLabel="Cambiar de mesa o restaurante">
            <Text style={styles.table}>Mesa {tableNumber} · cambiar</Text>
          </Pressable>
        </View>
        <View style={[styles.badge, online ? styles.badgeOnline : styles.badgeOffline]}>
          <Text style={[styles.badgeText, { color: online ? colors.success : colors.warning }]}>
            {online ? 'En línea' : 'Sin conexión'}
          </Text>
        </View>
      </View>

      <SectionList
        sections={sections}
        keyExtractor={(item) => item.id}
        stickySectionHeadersEnabled={false}
        contentContainerStyle={styles.list}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={props.refreshing} onRefresh={props.onRefresh} />}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            {!online || loaded.source === 'cache' ? (
              <Banner tone="info" title="Menú guardado en tu teléfono">
                {`Actualizado el ${formatShortDate(loaded.savedAt)}. Puedes armar tu pedido; se envía a cocina cuando vuelva la conexión. Los precios finales los confirma el restaurante.`}
              </Banner>
            ) : null}
            {notice ? <Banner tone={notice.tone}>{notice.text}</Banner> : null}
            {pendingCount > 0 ? (
              <Banner tone="info">{`${pendingCount === 1 ? 'Tienes 1 pedido' : `Tienes ${pendingCount} pedidos`} por enviar. Revísalo en tu pedido.`}</Banner>
            ) : null}
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Buscar en el menú"
              placeholderTextColor={colors.mutedForeground}
              style={styles.search}
            />
          </View>
        }
        ListEmptyComponent={<Text style={styles.empty}>{query ? 'Nada coincide con tu búsqueda.' : 'El menú está vacío.'}</Text>}
        renderSectionHeader={({ section }) => <Text style={styles.section}>{section.title}</Text>}
        renderItem={({ item }) => (
          <View style={styles.card}>
            <View style={styles.flex}>
              <Text style={styles.name}>{item.name}</Text>
              {item.description ? <Text style={styles.description}>{item.description}</Text> : null}
              <Text style={styles.price}>{formatMoney(item.price)}</Text>
            </View>
            <Stepper value={quantityOf(cart, item.id)} label={item.name} onChange={(q) => props.onSetQuantity(item, q)} />
          </View>
        )}
      />

      <View style={styles.footer}>
        <View style={styles.footerRow}>
          <View style={styles.flex}>
            <Button
              label={online ? 'Llamar al mesero' : 'Llamar (requiere señal)'}
              variant="secondary"
              onPress={props.onCallWaiter}
              disabled={!online}
              loading={props.callingWaiter}
            />
          </View>
          <View style={styles.flex}>
            <Button
              label={count ? `Mi pedido · ${count} · ${formatMoney(cartTotal(cart))}` : 'Mi pedido'}
              onPress={props.onOpenCart}
              disabled={count === 0 && pendingCount === 0 && props.historyCount === 0}
            />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 12, borderBottomWidth: 1, borderColor: colors.border },
  restaurant: { fontSize: 20, fontWeight: '800', color: colors.foreground },
  table: { color: colors.mutedForeground, marginTop: 2, textDecorationLine: 'underline' },
  badge: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1 },
  badgeOnline: { backgroundColor: colors.successSoft, borderColor: colors.success },
  badgeOffline: { backgroundColor: colors.warningSoft, borderColor: colors.warning },
  badgeText: { fontSize: 12, fontWeight: '700' },
  list: { padding: 16, paddingBottom: 24, gap: 10 },
  listHeader: { gap: 10, marginBottom: 4 },
  search: { backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: radius, paddingHorizontal: 12, minHeight: 44, color: colors.foreground },
  section: { fontSize: 13, fontWeight: '800', color: colors.mutedForeground, textTransform: 'uppercase', letterSpacing: 1, marginTop: 14, marginBottom: 6 },
  card: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.card, borderRadius: radius, borderWidth: 1, borderColor: colors.border, padding: 12, marginBottom: 8 },
  name: { fontSize: 16, fontWeight: '700', color: colors.foreground },
  description: { fontSize: 13, color: colors.mutedForeground, marginTop: 2, lineHeight: 18 },
  price: { fontSize: 15, fontWeight: '700', color: colors.foreground, marginTop: 6 },
  empty: { textAlign: 'center', color: colors.mutedForeground, marginTop: 32 },
  footer: { padding: 12, borderTopWidth: 1, borderColor: colors.border, backgroundColor: colors.background },
  footerRow: { flexDirection: 'row', gap: 10 },
});
