import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { fetchCategories } from '../api/catalog';
import { ApiError } from '../api/client';
import type { Category, Product } from '../api/types';
import { fetchProducts } from '../api/waiter';
import { Banner, Button } from '../ui/components';
import { colors, formatMoney, radius } from '../ui/theme';

interface Props {
  /** Producto que el servidor acaba de registrar, para confirmar la operación. */
  created: Product | null;
  canCreate: boolean;
  onBack: () => void;
  onNewProduct: () => void;
}

/** Consulta del catálogo: GET /products y GET /categories, mostrados en lista. */
export function ProductsScreen({ created, canCreate, onBack, onNewProduct }: Props) {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [categories, setCategories] = useState<Map<string, string>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (mode: 'initial' | 'refresh') => {
    if (mode === 'refresh') setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const [productData, categoryData] = await Promise.all([fetchProducts(), fetchCategories()]);
      setCategories(new Map(categoryData.map((category: Category) => [category.id, category.name])));
      setProducts([...productData].sort((a, b) => a.name.localeCompare(b.name, 'es')));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'No se pudo cargar el menú.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load('initial');
  }, [load]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" onPress={onBack} hitSlop={8}>
          <Text style={styles.back}>‹ Mesas</Text>
        </Pressable>
        <Text style={styles.title}>Menú</Text>
      </View>

      {created ? (
        <Banner tone="success" title="Producto registrado">
          El servidor guardó «{created.name}» a {formatMoney(created.price)} y ya aparece en el menú.
        </Banner>
      ) : null}

      {canCreate ? <Button label="Nuevo producto" onPress={onNewProduct} /> : null}

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={colors.foreground} />
          <Text style={styles.meta}>Consultando el menú en el servidor…</Text>
        </View>
      ) : error ? (
        <View style={styles.gap}>
          <Banner tone="error" title="Error al consultar el menú">
            {error}
          </Banner>
          <Button label="Reintentar" variant="secondary" onPress={() => void load('initial')} />
        </View>
      ) : (
        <FlatList
          data={products ?? []}
          keyExtractor={(product) => product.id}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load('refresh')} />}
          ListHeaderComponent={<Text style={styles.meta}>{products?.length ?? 0} productos activos</Text>}
          ListEmptyComponent={<Text style={styles.meta}>No hay productos activos en el menú.</Text>}
          renderItem={({ item }) => (
            <View style={[styles.product, item.id === created?.id && styles.productNew]}>
              <View style={styles.flex}>
                <Text style={styles.productName}>{item.name}</Text>
                <Text style={styles.meta} numberOfLines={2}>
                  {categories.get(item.categoryId) ?? 'Sin categoría'}
                  {item.description ? ` · ${item.description}` : ''}
                </Text>
              </View>
              <Text style={styles.price}>{formatMoney(item.price)}</Text>
            </View>
          )}
        />
      )}
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
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  list: { gap: 8, paddingBottom: 24 },
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
  productNew: { borderColor: colors.success, backgroundColor: colors.successSoft },
  productName: { fontSize: 15, fontWeight: '600', color: colors.foreground },
  price: { fontSize: 16, fontWeight: '700', color: colors.foreground },
});
