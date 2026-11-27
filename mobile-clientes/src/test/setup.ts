// Expo define __DEV__ en la app; en las pruebas vale true salvo que una prueba
// lo cambie con vi.stubGlobal('__DEV__', false).
(globalThis as { __DEV__?: boolean }).__DEV__ = true;
