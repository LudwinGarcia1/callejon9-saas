import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { fetchCatalog, fetchCategories, setProductActive } from '../api/catalog';
import { ApiError } from '../api/client';
import type { Category, Product } from '../api/types';
import { Banner, Button } from '../ui/components';
import { colors, formatMoney, radius } from '../ui/theme';
import type { SaveMode } from './ProductFormScreen';

/** Resultado de la última operación de escritura, para confirmarla en pantalla. */
export interface CatalogNotice {
  product: Product;
  mode: SaveMode;
}

interface Props {
  /** Producto que el servidor acaba de registrar o actualizar. */
  notice: CatalogNotice | null;
  /** Solo ADMIN registra, edita y da de baja; el backend lo vuelve a exigir con @PreAuthorize. */
  canManage: boolean;
  onBack: () => void;
  onNewProduct: () => void;
  onEditProduct: (product: Product) => void;
}

type Feedback = { tone: 'success' | 'error'; title: string; message: string };

const byName = (a: Product, b: Product) => a.name.localeCompare(b.name, 'es');

/**
 * Consulta del catálogo: GET /products y GET /categories, mostrados en lista.
 * Para ADMIN también lista los productos inactivos y permite editarlos (PUT)
 * o darlos de alta y de baja (PATCH).
 */
export function ProductsScreen({ notice, canManage, onBack, onNewProduct, onEditProduct }: Props) {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [categories, setCategories] = useState<Map<string, string>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [toggling, setToggling] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(
    notice
      ? notice.mode === 'created'
        ? {
            tone: 'success',
            title: 'Producto registrado',
            message: `El servidor guardó «${notice.product.name}» a ${formatMoney(notice.product.price)} y ya aparece en el menú.`,
          }
        : {
            tone: 'success',
            title: 'Producto actualizado',
            message: `El servidor guardó los cambios de «${notice.product.name}» (${formatMoney(notice.product.price)}).`,
          }
      : null,
  );

  const load = useCallback(
    async (mode: 'initial' | 'refresh') => {
      if (mode === 'refresh') setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const [productData, categoryData] = await Promise.all([fetchCatalog(canManage), fetchCategories()]);
        setCategories(new Map(categoryData.map((category: Category) => [category.id, category.name])));
        setProducts([...productData].sort(byName));
      } catch (cause) {
        setError(cause instanceof ApiError ? cause.message : 'No se pudo cargar el menú.');
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [canManage],
  );

  useEffect(() => {
    void load('initial');
  }, [load]);

  async function toggleActive(product: Product) {
    setToggling(product.id);
    setFeedback(null);
    try {
      const saved = await setProductActive(product.id, !product.active);
      setProducts((current) => current?.map((item) => (item.id === saved.id ? saved : item)) ?? null);
      setFeedback({
        tone: 'success',
        title: saved.active ? 'Producto activado' : 'Producto desactivado',
        message: saved.active
          ? `«${saved.name}» vuelve a aparecer en el menú de los meseros.`
          : `«${saved.name}» ya no aparece en el menú de los meseros. Las órdenes anteriores no cambian.`,
      });
    } catch (cause) {
      setFeedback({
        tone: 'error',
        title: 'El cambio no se guardó',
        message: cause instanceof ApiError ? cause.message : 'Ocurrió un error inesperado. Intenta de nuevo.',
      });
    } finally {
      setToggling(null);
    }
  }

  const activeCount = products?.filter((product) => product.active).length ?? 0;
  const inactiveCount = (products?.length ?? 0) - activeCount;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" onPress={onBack} hitSlop={8}>
          <Text style={styles.back}>‹ Mesas</Text>
        </Pressable>
        <Text style={styles.title}>Menú</Text>
      </View>

      {feedback ? (
        <Banner tone={feedback.tone} title={feedback.title}>
          {feedback.message}
        </Banner>
      ) : null}

      {canManage ? <Button label="Nuevo producto" onPress={onNewProduct} /> : null}

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
          ListHeaderComponent={
            <Text style={styles.meta}>
              {activeCount} productos activos
              {canManage && inactiveCount > 0 ? ` · ${inactiveCount} inactivos` : ''}
              {canManage ? ' · toca un producto para editarlo' : ''}
            </Text>
          }
          ListEmptyComponent={<Text style={styles.meta}>No hay productos en el menú.</Text>}
          renderItem={({ item }) => (
            <View
              style={[
                styles.product,
                item.id === notice?.product.id && styles.productNew,
                !item.active && styles.productInactive,
              ]}
            >
              <Pressable
                style={styles.flex}
                accessibilityRole={canManage ? 'button' : undefined}
                accessibilityLabel={canManage ? `Editar ${item.name}` : undefined}
                disabled={!canManage || toggling !== null}
                onPress={() => onEditProduct(item)}
              >
                <Text style={styles.productName}>
                  {item.name}
                  {item.active ? '' : ' (inactivo)'}
                </Text>
                <Text style={styles.meta} numberOfLines={2}>
                  {(item.categoryId && categories.get(item.categoryId)) ?? 'Sin categoría'}
                  {item.description ? ` · ${item.description}` : ''}
                </Text>
              </Pressable>
              <View style={styles.side}>
                <Text style={styles.price}>{formatMoney(item.price)}</Text>
                {canManage ? (
                  toggling === item.id ? (
                    <ActivityIndicator size="small" color={colors.foreground} />
                  ) : (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`${item.active ? 'Desactivar' : 'Activar'} ${item.name}`}
                      disabled={toggling !== null}
                      hitSlop={8}
                      onPress={() => void toggleActive(item)}
                    >
                      <Text style={[styles.toggle, !item.active && styles.toggleOn]}>
                        {item.active ? 'Desactivar' : 'Activar'}
                      </Text>
                    </Pressable>
                  )
                ) : null}
              </View>
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
  productInactive: { opacity: 0.6 },
  productName: { fontSize: 15, fontWeight: '600', color: colors.foreground },
  side: { alignItems: 'flex-end', gap: 6, minWidth: 84 },
  price: { fontSize: 16, fontWeight: '700', color: colors.foreground },
  toggle: { fontSize: 13, fontWeight: '600', color: colors.destructive },
  toggleOn: { color: colors.success },
});
