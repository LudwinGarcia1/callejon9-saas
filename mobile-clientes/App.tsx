import AsyncStorage from '@react-native-async-storage/async-storage';
import NetInfo from '@react-native-community/netinfo';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, BackHandler, Linking, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { ApiError } from './src/api/client';
import { callWaiter, fetchCatalog, placeTableOrder } from './src/api/public';
import type { Product } from './src/api/types';
import { addProduct, cartTotal, reconcileWithCatalog, setNotes, setQuantity, toOrderItems, type Cart } from './src/domain/cart';
import { parseTableLink } from './src/domain/tableLink';
import { loadCatalog, readCachedCatalog, type LoadedCatalog } from './src/offline/catalogCache';
import { historyForTable, type SentRecord } from './src/offline/orderHistory';
import { discard, enqueue, flush, listPending, sendOne, type PendingOrder } from './src/offline/outbox';
import { readJson, writeJson, type KeyValueStore } from './src/offline/storage';
import { CartScreen } from './src/screens/CartScreen';
import { MenuScreen, type Notice } from './src/screens/MenuScreen';
import { ScannerScreen } from './src/screens/ScannerScreen';
import { SetupScreen } from './src/screens/SetupScreen';
import { OUTBOX_AUTO_SEND_WINDOW_MS } from './src/config';
import { colors } from './src/ui/theme';

const store: KeyValueStore = AsyncStorage;

interface TableRef {
  slug: string;
  tableNumber: number;
}

type Screen = 'boot' | 'setup' | 'scan' | 'menu' | 'cart';

const TABLE_KEY = 'table';
const RECONNECT_RETRY_MS = 3_000;
const cartKey = (t: TableRef) => `cart:${t.slug}:${t.tableNumber}`;

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'Algo salió mal. Intenta de nuevo.';
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('boot');
  const [table, setTable] = useState<TableRef | null>(null);
  const [loaded, setLoaded] = useState<LoadedCatalog | null>(null);
  const [cart, setCart] = useState<Cart>([]);
  const [pending, setPending] = useState<PendingOrder[]>([]);
  const [history, setHistory] = useState<SentRecord[]>([]);
  const [online, setOnline] = useState(true);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [setupError, setSetupError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [sending, setSending] = useState(false);
  const [callingWaiter, setCallingWaiter] = useState(false);
  const [busyPendingId, setBusyPendingId] = useState<string | null>(null);
  const flushing = useRef(false);
  const tableRef = useRef<TableRef | null>(null);
  tableRef.current = table;
  const onlineRef = useRef(true);
  onlineRef.current = online;

  /** Pedidos ya enviados desde esta mesa, leídos del teléfono (funciona sin conexión). */
  const reloadHistory = useCallback(async (target: TableRef | null = tableRef.current) => {
    setHistory(target ? await historyForTable(store, target.slug, target.tableNumber) : []);
  }, []);

  /** Envía lo que quedó guardado sin conexión. Nunca corre dos veces a la vez. */
  const flushOutbox = useCallback(async () => {
    if (flushing.current || !onlineRef.current) return;
    flushing.current = true;
    try {
      const result = await flush(store, (o) => placeTableOrder(o.slug, o.tableNumber, o.items), {
        autoSendWindowMs: OUTBOX_AUTO_SEND_WINDOW_MS,
      });
      setPending(result.remaining);
      if (result.sent.length > 0) {
        await reloadHistory();
        const folios = result.sent.map((s) => s.order.folio).join(', ');
        setNotice({ tone: 'success', text: `Volvió la conexión: enviamos tu pedido a cocina (folio ${folios}).` });
      }
    } finally {
      flushing.current = false;
    }
  }, [reloadHistory]);

  /** Muestra un menú y el carrito guardado de esa mesa. */
  const showCatalog = useCallback(async (next: TableRef, result: LoadedCatalog) => {
    const savedCart = (await readJson<Cart>(store, cartKey(next))) ?? [];
    const reconciled = reconcileWithCatalog(savedCart, result.catalog.products);
    // Solo se escribe lo que cambió: el almacenamiento puede ser lento en teléfonos modestos.
    const sameTable = tableRef.current?.slug === next.slug && tableRef.current.tableNumber === next.tableNumber;
    await Promise.all([
      sameTable ? null : writeJson(store, TABLE_KEY, next),
      JSON.stringify(reconciled) === JSON.stringify(savedCart) ? null : writeJson(store, cartKey(next), reconciled),
    ]);
    setTable(next);
    setLoaded(result);
    setCart(reconciled);
    await reloadHistory(next);
    setScreen((current) => (current === 'cart' ? 'cart' : 'menu'));
  }, [reloadHistory]);

  /**
   * Abre una mesa. Si hay una copia del menú, se muestra al instante y se
   * actualiza en segundo plano: una red lenta o colgada nunca deja al
   * comensal esperando frente a una pantalla vacía.
   */
  const openTable = useCallback(async (next: TableRef, options: { silent?: boolean } = {}): Promise<LoadedCatalog['source'] | null> => {
    setSetupError(null);
    const cached = await readCachedCatalog(store, next.slug);
    if (cached) {
      await showCatalog(next, { catalog: cached.catalog, source: 'cache', savedAt: cached.savedAt });
      try {
        const fresh = await loadCatalog(store, next.slug, fetchCatalog);
        await showCatalog(next, fresh);
        return fresh.source;
      } catch (error) {
        if (error instanceof ApiError && error.kind === 'notFound') {
          setSetupError(error.message);
          setScreen('setup');
        }
        return 'cache';
      }
    }

    if (!options.silent) setLoading(true);
    try {
      const fresh = await loadCatalog(store, next.slug, fetchCatalog);
      await showCatalog(next, fresh);
      return fresh.source;
    } catch (error) {
      const message =
        error instanceof ApiError && (error.kind === 'network' || error.kind === 'timeout')
          ? 'No hay conexión y este menú aún no está guardado en tu teléfono. Conéctate una vez para descargarlo.'
          : errorText(error);
      setSetupError(message);
      setScreen('setup');
      return null;
    } finally {
      setLoading(false);
    }
  }, [showCatalog]);

  // Arranque: última mesa usada, pedidos pendientes y enlace del QR si la app se abrió con uno.
  useEffect(() => {
    void (async () => {
      const [initialPending, initialUrl, saved] = await Promise.all([
        listPending(store),
        Linking.getInitialURL().catch(() => null),
        readJson<TableRef>(store, TABLE_KEY),
      ]);
      setPending(initialPending);
      const fromLink = initialUrl ? parseTableLink(initialUrl) : null;
      if (fromLink?.tableNumber) {
        await openTable({ slug: fromLink.slug, tableNumber: fromLink.tableNumber });
        return;
      }
      if (saved) {
        await openTable(saved);
      } else {
        setScreen('setup');
      }
    })();

    const linkSub = Linking.addEventListener('url', ({ url }) => {
      const ref = parseTableLink(url);
      if (ref?.tableNumber) void openTable({ slug: ref.slug, tableNumber: ref.tableNumber });
    });
    return () => linkSub.remove();
  }, [openTable]);

  // Conectividad: al recuperar la red se envían los pendientes y se refresca el menú.
  useEffect(() => {
    // Android avisa que hay red un instante antes de que se pueda usar: si el
    // menú sigue saliendo de la copia guardada, se reintenta una vez.
    async function reconnect() {
      await flushOutbox();
      const current = tableRef.current;
      if (!current) return;
      const refreshed = await openTable(current, { silent: true });
      if (refreshed === 'cache' && onlineRef.current) {
        await new Promise((resolve) => setTimeout(resolve, RECONNECT_RETRY_MS));
        await flushOutbox();
        await openTable(current, { silent: true });
      }
    }

    let wasOnline = true;
    const unsubscribe = NetInfo.addEventListener((state) => {
      const isOnline = Boolean(state.isConnected) && state.isInternetReachable !== false;
      onlineRef.current = isOnline;
      setOnline(isOnline);
      if (isOnline && !wasOnline) {
        void reconnect();
      }
      wasOnline = isOnline;
    });
    const appStateSub = AppState.addEventListener('change', (state) => {
      if (state === 'active') void flushOutbox();
    });
    void flushOutbox();
    return () => {
      unsubscribe();
      appStateSub.remove();
    };
  }, [flushOutbox, openTable]);

  // Botón atrás de Android: del carrito vuelve al menú.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (screen === 'cart') {
        setScreen('menu');
        return true;
      }
      if (screen === 'scan') {
        setScreen('setup');
        return true;
      }
      return false;
    });
    return () => sub.remove();
  }, [screen]);

  function updateCart(next: Cart) {
    setCart(next);
    if (table) void writeJson(store, cartKey(table), next);
  }

  function onSetQuantity(product: Product, quantity: number) {
    const base = cart.some((l) => l.productId === product.id) ? cart : addProduct(cart, product);
    updateCart(setQuantity(base, product.id, quantity));
  }

  async function onSend() {
    if (!table || cart.length === 0) return;
    setSending(true);
    setNotice(null);
    try {
      const queued = await enqueue(store, {
        slug: table.slug,
        tableNumber: table.tableNumber,
        items: toOrderItems(cart),
        summary: cart.map((l) => `${l.quantity} × ${l.name}`).join(', '),
        lines: cart.map((l) => ({ name: l.name, quantity: l.quantity, unitPrice: l.unitPrice, notes: l.notes.trim() })),
        estimatedTotal: cartTotal(cart),
      });
      if (!onlineRef.current) {
        // Sin red no se intenta la petición: el pedido queda guardado y sale al reconectar.
        updateCart([]);
        setPending(await listPending(store));
        setNotice({ tone: 'info', text: 'Sin conexión: guardamos tu pedido y lo enviaremos a cocina en cuanto vuelva la señal.' });
        setScreen('menu');
        return;
      }
      const result = await sendOne(store, queued.localId, (o) => placeTableOrder(o.slug, o.tableNumber, o.items));
      const left = result.remaining.find((o) => o.localId === queued.localId);

      if (result.sent) {
        updateCart([]);
        setPending(result.remaining);
        await reloadHistory();
        setNotice({ tone: 'success', text: `Pedido enviado a cocina. Folio ${result.sent.order.folio}.` });
        setScreen('menu');
      } else if (left?.status === 'rejected') {
        // El servidor lo rechazó (producto agotado, mesa inactiva…): se conserva el carrito para corregirlo.
        setPending(await discard(store, queued.localId));
        setNotice({ tone: 'error', text: left.message ?? 'No se pudo enviar el pedido.' });
      } else {
        updateCart([]);
        setPending(result.remaining);
        setNotice(
          left?.status === 'unconfirmed'
            ? { tone: 'info', text: left.message ?? 'No pudimos confirmar el envío.' }
            : { tone: 'info', text: 'Sin conexión: guardamos tu pedido y lo enviaremos a cocina en cuanto vuelva la señal.' },
        );
        setScreen('menu');
      }
    } catch (error) {
      setNotice({ tone: 'error', text: errorText(error) });
    } finally {
      setSending(false);
    }
  }

  async function onSendPending(localId: string) {
    setBusyPendingId(localId);
    try {
      const result = await sendOne(store, localId, (o) => placeTableOrder(o.slug, o.tableNumber, o.items));
      setPending(result.remaining);
      if (result.sent) await reloadHistory();
      if (result.sent) setNotice({ tone: 'success', text: `Pedido enviado a cocina. Folio ${result.sent.order.folio}.` });
    } finally {
      setBusyPendingId(null);
    }
  }

  async function onCallWaiter() {
    if (!table) return;
    setCallingWaiter(true);
    try {
      const result = await callWaiter(table.slug, table.tableNumber);
      setNotice({ tone: 'success', text: result.message });
    } catch (error) {
      setNotice({ tone: 'error', text: errorText(error) });
    } finally {
      setCallingWaiter(false);
    }
  }

  async function onRefresh() {
    if (!table) return;
    setRefreshing(true);
    await openTable(table, { silent: true });
    await flushOutbox();
    setRefreshing(false);
  }

  let content;
  if (screen === 'boot') {
    content = (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} size="large" />
      </View>
    );
  } else if (screen === 'scan') {
    content = (
      <ScannerScreen
        onCancel={() => setScreen('setup')}
        onScanned={(ref) => {
          setScreen('setup');
          void openTable({ slug: ref.slug, tableNumber: ref.tableNumber! });
        }}
      />
    );
  } else if (screen === 'setup' || !table || !loaded) {
    content = (
      <SetupScreen
        initialSlug={table?.slug ?? ''}
        initialTable={table ? String(table.tableNumber) : ''}
        error={setupError}
        loading={loading}
        onSubmit={(slug, tableNumber) => void openTable({ slug, tableNumber })}
        onScan={() => {
          setSetupError(null);
          setScreen('scan');
        }}
      />
    );
  } else if (screen === 'cart') {
    content = (
      <CartScreen
        cart={cart}
        pending={pending}
        history={history}
        online={online}
        sending={sending}
        busyPendingId={busyPendingId}
        notice={notice}
        onSetQuantity={(id, q) => updateCart(setQuantity(cart, id, q))}
        onSetNotes={(id, notes) => updateCart(setNotes(cart, id, notes))}
        onSend={() => void onSend()}
        onBack={() => setScreen('menu')}
        onSendPending={(id) => void onSendPending(id)}
        onDiscardPending={(id) => void discard(store, id).then(setPending)}
      />
    );
  } else {
    content = (
      <MenuScreen
        loaded={loaded}
        tableNumber={table.tableNumber}
        cart={cart}
        online={online}
        refreshing={refreshing}
        callingWaiter={callingWaiter}
        notice={notice}
        pendingCount={pending.length}
        historyCount={history.length}
        onSetQuantity={onSetQuantity}
        onRefresh={() => void onRefresh()}
        onOpenCart={() => {
          setNotice(null);
          setScreen('cart');
        }}
        onCallWaiter={() => void onCallWaiter()}
        onChangeTable={() => {
          setNotice(null);
          setScreen('setup');
        }}
      />
    );
  }

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
        <StatusBar style="dark" />
        {content}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
