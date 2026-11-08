import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { fetchCurrentUser, logout } from './src/api/auth';
import { setSessionExpiredHandler } from './src/api/client';
import type { Order, Product, RestaurantTable, Role } from './src/api/types';
import {
  configureForegroundPresentation,
  registerForPushNotifications,
  subscribeToNotifications,
  type InboxItem,
  type PushRegistration,
} from './src/notifications/push';
import { LoginScreen } from './src/screens/LoginScreen';
import { NotificationsScreen } from './src/screens/NotificationsScreen';
import { OrderScreen } from './src/screens/OrderScreen';
import { ProductFormScreen } from './src/screens/ProductFormScreen';
import { ProductsScreen, type CatalogNotice } from './src/screens/ProductsScreen';
import { ResultScreen } from './src/screens/ResultScreen';
import { TablesScreen } from './src/screens/TablesScreen';
import { colors } from './src/ui/theme';

// Debe configurarse antes de que llegue la primera notificación en primer plano.
configureForegroundPresentation();

/** Solo estos roles pueden tomar pedidos; el backend lo vuelve a exigir. */
const WAITER_ROLES: Role[] = ['WAITER', 'ADMIN'];
const WRONG_ROLE_MESSAGE = 'Esta app es para meseros. Tu cuenta no tiene ese rol.';

interface Session {
  userName: string;
  restaurantName: string | null;
  role: Role;
}

type Screen =
  | { name: 'checking' }
  | { name: 'login'; notice: string | null }
  | { name: 'tables'; welcome: boolean }
  | { name: 'order'; table: RestaurantTable }
  | { name: 'result'; order: Order; added: boolean }
  | { name: 'products'; notice: CatalogNotice | null }
  | { name: 'productForm'; product: Product | null }
  | { name: 'notifications' };

export default function App() {
  const [screen, setScreen] = useState<Screen>({ name: 'checking' });
  const [session, setSession] = useState<Session | null>(null);
  const [registration, setRegistration] = useState<PushRegistration | null>(null);
  const [registering, setRegistering] = useState(false);
  const [inbox, setInbox] = useState<InboxItem[]>([]);
  const [selected, setSelected] = useState<InboxItem | null>(null);
  // Un toque que llega antes del login se atiende cuando haya sesión.
  const pendingOpen = useRef(false);
  const sessionRef = useRef<Session | null>(null);
  sessionRef.current = session;

  const register = useCallback(async () => {
    setRegistering(true);
    try {
      const result = await registerForPushNotifications();
      setRegistration(result);
      if (result.status === 'granted' && __DEV__) {
        // Facilita copiar el token desde la terminal de Metro durante la práctica.
        console.log(`[push] token del dispositivo: ${result.token}`);
      }
    } finally {
      setRegistering(false);
    }
  }, []);

  // Las suscripciones se crean al arrancar para no perder el toque que abrió la app.
  useEffect(() => {
    const addToInbox = (item: InboxItem) =>
      setInbox((items) => [item, ...items.filter((existing) => existing.id !== item.id)]);
    return subscribeToNotifications({
      onReceived: addToInbox,
      onOpened: (item) => {
        addToInbox(item);
        setSelected(item);
        if (sessionRef.current) setScreen({ name: 'notifications' });
        else pendingOpen.current = true;
      },
    });
  }, []);

  // El dispositivo se registra una vez que hay una sesión válida.
  useEffect(() => {
    if (!session) return;
    if (!registration) void register();
    if (pendingOpen.current) {
      pendingOpen.current = false;
      setScreen({ name: 'notifications' });
    }
  }, [session, registration, register]);

  // Si el usuario concede el permiso desde Ajustes, se nota al volver a la app.
  useEffect(() => {
    // Solo tras un intento fallido: el registro inicial lo hace el efecto anterior.
    if (!session || !registration || registration.status === 'granted') return;
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active' && !registering) void register();
    });
    return () => subscription.remove();
  }, [session, registration, registering, register]);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setSession(null);
      setScreen({ name: 'login', notice: 'Tu sesión expiró. Inicia sesión de nuevo.' });
    });

    // Si el almacén de cookies conserva una sesión vigente, se reanuda.
    fetchCurrentUser()
      .then((me) => {
        if (!WAITER_ROLES.includes(me.role)) {
          void logout();
          setScreen({ name: 'login', notice: null });
          return;
        }
        setSession({ userName: me.fullName, restaurantName: me.restaurantName, role: me.role });
        setScreen({ name: 'tables', welcome: false });
      })
      .catch(() => setScreen({ name: 'login', notice: null }));

    return () => setSessionExpiredHandler(null);
  }, []);

  async function handleLogout() {
    await logout();
    setSession(null);
    // El buzón es de la sesión: no queda a la vista de la siguiente persona.
    setInbox([]);
    setSelected(null);
    setScreen({ name: 'login', notice: null });
  }

  function renderScreen() {
    switch (screen.name) {
      case 'checking':
        return (
          <View style={styles.center}>
            <ActivityIndicator size="large" color={colors.foreground} />
          </View>
        );
      case 'login':
        return (
          <LoginScreen
            notice={screen.notice}
            onAuthenticated={async (user) => {
              if (!WAITER_ROLES.includes(user.role)) {
                await logout();
                setScreen({ name: 'login', notice: WRONG_ROLE_MESSAGE });
                return;
              }
              const me = await fetchCurrentUser().catch(() => null);
              setSession({ userName: user.fullName, restaurantName: me?.restaurantName ?? null, role: user.role });
              setScreen({ name: 'tables', welcome: true });
            }}
          />
        );
      case 'tables':
        return (
          <TablesScreen
            userName={session?.userName ?? ''}
            restaurantName={session?.restaurantName ?? null}
            welcome={screen.welcome}
            onSelectTable={(table) => setScreen({ name: 'order', table })}
            onOpenMenu={() => setScreen({ name: 'products', notice: null })}
            onOpenNotifications={() => setScreen({ name: 'notifications' })}
            onLogout={handleLogout}
          />
        );
      case 'order':
        return (
          <OrderScreen
            table={screen.table}
            onBack={() => setScreen({ name: 'tables', welcome: false })}
            onSubmitted={(order, added) => setScreen({ name: 'result', order, added })}
          />
        );
      case 'result':
        return (
          <ResultScreen
            order={screen.order}
            added={screen.added}
            onDone={() => setScreen({ name: 'tables', welcome: false })}
          />
        );
      case 'products':
        return (
          <ProductsScreen
            notice={screen.notice}
            // Solo ADMIN registra, edita y da de baja; el backend lo vuelve a exigir con @PreAuthorize.
            canManage={session?.role === 'ADMIN'}
            onBack={() => setScreen({ name: 'tables', welcome: false })}
            onNewProduct={() => setScreen({ name: 'productForm', product: null })}
            onEditProduct={(product) => setScreen({ name: 'productForm', product })}
          />
        );
      case 'productForm':
        return (
          <ProductFormScreen
            // La key reinicia el formulario al pasar de editar un producto a otro.
            key={screen.product?.id ?? 'new'}
            product={screen.product}
            onCancel={() => setScreen({ name: 'products', notice: null })}
            onSaved={(product, mode) => setScreen({ name: 'products', notice: { product, mode } })}
          />
        );
      case 'notifications':
        return (
          <NotificationsScreen
            registration={registration}
            registering={registering}
            inbox={inbox}
            selected={selected}
            onRetry={() => void register()}
            onSelect={setSelected}
            onBack={() => setScreen({ name: 'tables', welcome: false })}
          />
        );
    }
  }

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.root}>
        <StatusBar style="dark" />
        {renderScreen()}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
