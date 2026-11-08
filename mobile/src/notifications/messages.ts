/**
 * Contrato de las notificaciones de seguimiento de solicitudes.
 *
 * Este módulo es TypeScript puro (sin React Native) para que lo compartan la
 * app, que valida lo que recibe, y el script remoto `scripts/send-push.mts`,
 * que arma lo que envía. Así emisor y receptor no pueden divergir.
 */

/** Ciclo de vida de una solicitud de servicio. */
export const REQUEST_STATUSES = ['REGISTERED', 'RECEIVED', 'IN_PROGRESS', 'COMPLETED'] as const;
export type RequestStatus = (typeof REQUEST_STATUSES)[number];

/** El registro es un evento propio; los otros tres estados son cambios de estado. */
export type PushEventType = 'REGISTERED' | 'STATUS_CHANGED';

export const EVENT_TYPE_LABEL: Record<PushEventType, string> = {
  REGISTERED: 'Registro de solicitud',
  STATUS_CHANGED: 'Cambio de estado',
};

interface StatusCopy {
  label: string;
  title: string;
  body: string;
}

export const STATUS_COPY: Record<RequestStatus, StatusCopy> = {
  REGISTERED: {
    label: 'Registrada',
    title: 'Solicitud registrada',
    body: 'Tu solicitud fue registrada correctamente',
  },
  RECEIVED: {
    label: 'Recibida',
    title: 'Actualización de solicitud',
    body: 'Tu solicitud fue recibida por el personal responsable',
  },
  IN_PROGRESS: {
    label: 'En proceso',
    title: 'Actualización de solicitud',
    body: 'Tu solicitud se encuentra en proceso',
  },
  COMPLETED: {
    label: 'Finalizada',
    title: 'Actualización de solicitud',
    body: 'Tu solicitud ha finalizado',
  },
};

/** Datos adjuntos (campo `data`) que viajan con cada notificación. */
export interface RequestNotificationData {
  eventType: PushEventType;
  status: RequestStatus;
  requestId: string;
}

/** Mensaje en el formato que acepta https://exp.host/--/api/v2/push/send. */
export interface ExpoPushMessage {
  to: string;
  title: string;
  body: string;
  data: RequestNotificationData;
  sound: 'default';
  priority: 'high';
  channelId: string;
  ttl: number;
}

export const EXPO_PUSH_SEND_URL = 'https://exp.host/--/api/v2/push/send';
export const EXPO_PUSH_RECEIPTS_URL = 'https://exp.host/--/api/v2/push/getReceipts';

/** Canal de Android donde se publican estas notificaciones. */
export const ANDROID_CHANNEL_ID = 'solicitudes';

/** SOL- seguido de 3 a 8 dígitos, p. ej. SOL-001. */
export const REQUEST_ID_PATTERN = /^SOL-\d{3,8}$/;

/** Formato del token que entrega getExpoPushTokenAsync. */
export const EXPO_PUSH_TOKEN_PATTERN = /^Expo(nent)?PushToken\[[A-Za-z0-9_-]+\]$/;

/** Una hora: un aviso de estado que llega más tarde ya no sirve. */
const MESSAGE_TTL_SECONDS = 3600;

export function isRequestStatus(value: unknown): value is RequestStatus {
  return typeof value === 'string' && (REQUEST_STATUSES as readonly string[]).includes(value);
}

export function eventTypeFor(status: RequestStatus): PushEventType {
  return status === 'REGISTERED' ? 'REGISTERED' : 'STATUS_CHANGED';
}

/**
 * Arma el mensaje push de una solicitud. Lanza un Error si el token o el
 * identificador no tienen el formato esperado, para no enviar basura.
 */
export function buildPushMessage(input: { to: string; status: RequestStatus; requestId: string }): ExpoPushMessage {
  const to = input.to.trim();
  const requestId = input.requestId.trim().toUpperCase();
  if (!EXPO_PUSH_TOKEN_PATTERN.test(to)) {
    throw new Error('El destinatario debe ser un token de Expo: ExponentPushToken[…].');
  }
  if (!isRequestStatus(input.status)) {
    throw new Error(`Estado desconocido. Usa uno de: ${REQUEST_STATUSES.join(', ')}.`);
  }
  if (!REQUEST_ID_PATTERN.test(requestId)) {
    throw new Error('El identificador debe tener la forma SOL-001.');
  }
  const copy = STATUS_COPY[input.status];
  return {
    to,
    title: copy.title,
    body: copy.body,
    data: { eventType: eventTypeFor(input.status), status: input.status, requestId },
    sound: 'default',
    priority: 'high',
    channelId: ANDROID_CHANNEL_ID,
    ttl: MESSAGE_TTL_SECONDS,
  };
}

/**
 * Valida el `data` de una notificación recibida. Cualquier remitente que
 * conozca el token puede enviar un push, así que la app no confía en el
 * contenido: solo acepta valores de una lista cerrada y descarta lo demás.
 */
export function parseRequestNotificationData(data: unknown): RequestNotificationData | null {
  if (typeof data !== 'object' || data === null) return null;
  const record = data as Record<string, unknown>;
  const { status, requestId } = record;
  if (!isRequestStatus(status)) return null;
  if (typeof requestId !== 'string' || !REQUEST_ID_PATTERN.test(requestId)) return null;
  const eventType = eventTypeFor(status);
  if (record.eventType !== undefined && record.eventType !== eventType) return null;
  return { eventType, status, requestId };
}

/**
 * Normaliza la fecha de una notificación a milisegundos. iOS la entrega en
 * segundos desde 1970 y Android en milisegundos; sin esto, en iPhone la hora
 * aparecía como enero de 1970.
 */
export function toEpochMillis(value: number | null | undefined, fallback = Date.now()): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return fallback;
  // Cualquier instante posterior a marzo de 1973 en milisegundos supera 1e11.
  return value < 1e11 ? Math.round(value * 1000) : value;
}
