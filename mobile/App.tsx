import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { fetchCurrentUser, logout } from './src/api/auth';
import { setSessionExpiredHandler } from './src/api/client';
import type { Order, Product, RestaurantTable, Role } from './src/api/types';
import { LoginScreen } from './src/screens/LoginScreen';
import { OrderScreen } from './src/screens/OrderScreen';
import { ProductFormScreen } from './src/screens/ProductFormScreen';
import { ProductsScreen } from './src/screens/ProductsScreen';
import { ResultScreen } from './src/screens/ResultScreen';
import { TablesScreen } from './src/screens/TablesScreen';
import { colors } from './src/ui/theme';

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
  | { name: 'result'; order: Order }
  | { name: 'products'; created: Product | null }
  | { name: 'productForm' };

export default function App() {
  const [screen, setScreen] = useState<Screen>({ name: 'checking' });
  const [session, setSession] = useState<Session | null>(null);

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
            onOpenMenu={() => setScreen({ name: 'products', created: null })}
            onLogout={handleLogout}
          />
        );
      case 'order':
        return (
          <OrderScreen
            table={screen.table}
            onBack={() => setScreen({ name: 'tables', welcome: false })}
            onSubmitted={(order) => setScreen({ name: 'result', order })}
          />
        );
      case 'result':
        return <ResultScreen order={screen.order} onDone={() => setScreen({ name: 'tables', welcome: false })} />;
      case 'products':
        return (
          <ProductsScreen
            created={screen.created}
            // Solo ADMIN registra productos; el backend lo vuelve a exigir con @PreAuthorize.
            canCreate={session?.role === 'ADMIN'}
            onBack={() => setScreen({ name: 'tables', welcome: false })}
            onNewProduct={() => setScreen({ name: 'productForm' })}
          />
        );
      case 'productForm':
        return (
          <ProductFormScreen
            onCancel={() => setScreen({ name: 'products', created: null })}
            onCreated={(product) => setScreen({ name: 'products', created: product })}
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
