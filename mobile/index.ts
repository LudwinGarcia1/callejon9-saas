import { registerRootComponent } from 'expo';

import App from './App';
import { initSentry, withSentry } from './src/monitoring/sentry';

// Sentry se inicializa antes de montar la app para capturar también los errores de arranque.
initSentry();

// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(withSentry(App));
