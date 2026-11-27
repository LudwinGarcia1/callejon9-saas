import { describe, expect, it } from 'vitest';
import { parseTableLink, parseTableNumber } from '../domain/tableLink';

describe('enlace de mesa', () => {
  it('lee el enlace web del QR', () => {
    expect(parseTableLink('https://restohub.mx/m/callejon-9/4')).toEqual({ slug: 'callejon-9', tableNumber: 4 });
  });

  it('lee el enlace de la app y tolera query y diagonal final', () => {
    expect(parseTableLink('restohub://m/callejon-9/12/?src=qr')).toEqual({ slug: 'callejon-9', tableNumber: 12 });
  });

  it('acepta solo el código del restaurante', () => {
    expect(parseTableLink('  Callejon-9 ')).toEqual({ slug: 'callejon-9', tableNumber: null });
    expect(parseTableLink('https://restohub.mx/m/callejon-9')).toEqual({ slug: 'callejon-9', tableNumber: null });
  });

  it('rechaza códigos y mesas inválidos', () => {
    expect(parseTableLink('../admin')).toBeNull();
    expect(parseTableLink('https://restohub.mx/m/callejon-9/abc')).toBeNull();
    expect(parseTableNumber('0')).toBeNull();
    expect(parseTableNumber('7')).toBe(7);
  });
});
