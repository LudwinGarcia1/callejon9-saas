import { describe, expect, it } from 'vitest';
import { API_URL, CONFIG_ERROR, IS_SECURE_TRANSPORT, REQUEST_TIMEOUT_MS } from '../config';

describe('mobile/src/config (Configuración y validaciones de seguridad de transporte)', () => {
  it('define un tiempo límite de transporte de 15 segundos', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(15_000);
  });

  it('valida que la URL del servidor no viaje en texto plano en entornos no seguros', () => {
    if (API_URL) {
      expect(IS_SECURE_TRANSPORT).toBe(true);
      expect(API_URL.startsWith('https://')).toBe(true);
    } else {
      expect(CONFIG_ERROR).toBeDefined();
    }
  });
});
