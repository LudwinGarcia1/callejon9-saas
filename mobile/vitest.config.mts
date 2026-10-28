import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    // __DEV__ se define en tiempo de ejecución, no con `define`: así una prueba
    // puede simular una build de producción (__DEV__ = false) antes de importar
    // el módulo que lo lee.
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    unstubEnvs: true,
    unstubGlobals: true,
  },
});
