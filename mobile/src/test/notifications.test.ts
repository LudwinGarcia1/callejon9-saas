import { describe, expect, it } from 'vitest';
import {
  ANDROID_CHANNEL_ID,
  REQUEST_STATUSES,
  buildPushMessage,
  parseRequestNotificationData,
  toEpochMillis,
} from '../notifications/messages';

const TOKEN = 'ExponentPushToken[AbC123_-xyz]';

describe('buildPushMessage: mensaje que envía el servicio remoto', () => {
  it('arma el ejemplo de la práctica: cambio de estado de SOL-001 a en proceso', () => {
    expect(buildPushMessage({ to: TOKEN, status: 'IN_PROGRESS', requestId: 'SOL-001' })).toEqual({
      to: TOKEN,
      title: 'Actualización de solicitud',
      body: 'Tu solicitud se encuentra en proceso',
      data: { eventType: 'STATUS_CHANGED', status: 'IN_PROGRESS', requestId: 'SOL-001' },
      sound: 'default',
      priority: 'high',
      channelId: ANDROID_CHANNEL_ID,
      ttl: 3600,
    });
  });

  it('marca el registro como evento propio y no como cambio de estado', () => {
    const message = buildPushMessage({ to: TOKEN, status: 'REGISTERED', requestId: 'sol-002' });
    expect(message.title).toBe('Solicitud registrada');
    expect(message.data).toEqual({ eventType: 'REGISTERED', status: 'REGISTERED', requestId: 'SOL-002' });
  });

  it('tiene título y mensaje para los cuatro estados', () => {
    for (const status of REQUEST_STATUSES) {
      const message = buildPushMessage({ to: TOKEN, status, requestId: 'SOL-001' });
      expect(message.title.length).toBeGreaterThan(0);
      expect(message.body.length).toBeGreaterThan(0);
    }
  });

  it('rechaza un destinatario que no es token de Expo', () => {
    expect(() => buildPushMessage({ to: 'usuario@correo.com', status: 'RECEIVED', requestId: 'SOL-001' })).toThrow(
      /token de Expo/,
    );
  });

  it('rechaza un identificador con otro formato', () => {
    expect(() => buildPushMessage({ to: TOKEN, status: 'RECEIVED', requestId: '../admin' })).toThrow(/SOL-001/);
  });
});

describe('parseRequestNotificationData: validación de lo que recibe la app', () => {
  it('acepta datos válidos', () => {
    expect(parseRequestNotificationData({ eventType: 'STATUS_CHANGED', status: 'COMPLETED', requestId: 'SOL-010' })).toEqual({
      eventType: 'STATUS_CHANGED',
      status: 'COMPLETED',
      requestId: 'SOL-010',
    });
  });

  it('deriva el tipo de evento cuando no viene', () => {
    expect(parseRequestNotificationData({ status: 'REGISTERED', requestId: 'SOL-001' })?.eventType).toBe('REGISTERED');
  });

  it.each([
    ['sin data', undefined],
    ['un texto', 'SOL-001'],
    ['estado desconocido', { status: 'DELETED', requestId: 'SOL-001' }],
    ['identificador inválido', { status: 'RECEIVED', requestId: '<script>' }],
    ['tipo de evento incoherente', { eventType: 'REGISTERED', status: 'COMPLETED', requestId: 'SOL-001' }],
  ])('descarta %s', (_name, data) => {
    expect(parseRequestNotificationData(data)).toBeNull();
  });

  it('no copia campos extra del remitente', () => {
    const parsed = parseRequestNotificationData({ status: 'RECEIVED', requestId: 'SOL-001', url: 'https://malicioso.example' });
    expect(parsed).not.toHaveProperty('url');
  });
});

describe('toEpochMillis: fecha de la notificación en ambas plataformas', () => {
  it('convierte los segundos de iOS a milisegundos', () => {
    expect(toEpochMillis(1_794_174_516.009)).toBe(1_794_174_516_009);
  });

  it('respeta los milisegundos de Android', () => {
    expect(toEpochMillis(1_794_174_516_009)).toBe(1_794_174_516_009);
  });

  it('usa la hora actual si no hay fecha válida', () => {
    expect(toEpochMillis(0, 42)).toBe(42);
    expect(toEpochMillis(undefined, 42)).toBe(42);
    expect(toEpochMillis(Number.NaN, 42)).toBe(42);
  });
});

