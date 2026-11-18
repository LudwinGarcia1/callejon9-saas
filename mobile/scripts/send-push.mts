/**
 * Servicio remoto mínimo: envía una notificación de solicitud a un
 * dispositivo a través del servicio push de Expo (que entrega por FCM en
 * Android y por APNs en iOS).
 *
 * Uso (Node 22.18+ o 24, que ejecutan TypeScript sin compilar):
 *   pnpm push:send --to "ExponentPushToken[xxxx]" --status IN_PROGRESS --id SOL-001
 *   pnpm push:send --to "ExponentPushToken[xxxx]" --status REGISTERED --id SOL-001 --dry-run
 *
 * Opciones:
 *   --to       token del dispositivo (obligatorio; o variable EXPO_PUSH_TOKEN)
 *   --status   REGISTERED | RECEIVED | IN_PROGRESS | COMPLETED (por defecto REGISTERED)
 *   --id       identificador de la solicitud, p. ej. SOL-001 (por defecto SOL-001)
 *   --dry-run  imprime el mensaje sin enviarlo
 *   --receipt  espera unos segundos y consulta el recibo de entrega
 *
 * Si el proyecto de Expo tiene activada la seguridad reforzada de push, el
 * envío exige EXPO_ACCESS_TOKEN en el entorno. Nunca lo pases como argumento:
 * quedaría en el historial del shell.
 */
import { parseArgs } from 'node:util';
import {
  EXPO_PUSH_RECEIPTS_URL,
  EXPO_PUSH_SEND_URL,
  REQUEST_STATUSES,
  buildPushMessage,
  isRequestStatus,
} from '../src/notifications/messages.ts';

const RECEIPT_DELAY_MS = 5_000;

function fail(message: string): never {
  console.error(`Error: ${message}`);
  process.exit(1);
}

const { values } = parseArgs({
  options: {
    to: { type: 'string' },
    status: { type: 'string', default: 'REGISTERED' },
    id: { type: 'string', default: 'SOL-001' },
    'dry-run': { type: 'boolean', default: false },
    receipt: { type: 'boolean', default: false },
  },
});

const to = values.to ?? process.env.EXPO_PUSH_TOKEN;
if (!to) fail('falta --to con el token del dispositivo.');
const status = values.status.toUpperCase();
if (!isRequestStatus(status)) fail(`--status debe ser uno de: ${REQUEST_STATUSES.join(', ')}.`);

let message;
try {
  message = buildPushMessage({ to, status, requestId: values.id });
} catch (cause) {
  fail((cause as Error).message);
}

/** El token identifica un dispositivo: en la salida solo se muestra el inicio. */
const maskedTo = `${message.to.slice(0, 22)}…]`;
console.log('Mensaje preparado:');
console.log(JSON.stringify({ ...message, to: maskedTo }, null, 2));

if (values['dry-run']) {
  console.log('\n--dry-run: no se envió nada.');
  process.exit(0);
}

const headers: Record<string, string> = {
  Accept: 'application/json',
  'Accept-Encoding': 'gzip, deflate',
  'Content-Type': 'application/json',
};
if (process.env.EXPO_ACCESS_TOKEN) {
  headers.Authorization = `Bearer ${process.env.EXPO_ACCESS_TOKEN}`;
}

interface PushTicket {
  status: 'ok' | 'error';
  id?: string;
  message?: string;
  details?: { error?: string };
}

const response = await fetch(EXPO_PUSH_SEND_URL, { method: 'POST', headers, body: JSON.stringify([message]) });
const payload = (await response.json().catch(() => null)) as { data?: PushTicket[]; errors?: unknown } | null;
if (!response.ok || !payload?.data) {
  fail(`el servicio de Expo respondió ${response.status}: ${JSON.stringify(payload?.errors ?? payload)}`);
}

const [ticket] = payload.data;
console.log('\nTicket de envío:', JSON.stringify(ticket));
if (ticket.status === 'error') {
  // DeviceNotRegistered: el token ya no es válido y el backend debería olvidarlo.
  fail(`${ticket.details?.error ?? 'error'}: ${ticket.message ?? 'sin detalle'}`);
}

if (values.receipt && ticket.id) {
  console.log(`\nConsultando el recibo en ${RECEIPT_DELAY_MS / 1000} s…`);
  await new Promise((resolve) => setTimeout(resolve, RECEIPT_DELAY_MS));
  const receiptResponse = await fetch(EXPO_PUSH_RECEIPTS_URL, {
    method: 'POST',
    headers,
    body: JSON.stringify({ ids: [ticket.id] }),
  });
  const receipts = (await receiptResponse.json().catch(() => null)) as { data?: Record<string, unknown> } | null;
  console.log('Recibo:', JSON.stringify(receipts?.data?.[ticket.id] ?? 'aún no disponible; reintenta en unos minutos'));
}
