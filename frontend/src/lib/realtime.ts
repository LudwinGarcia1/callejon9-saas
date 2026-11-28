import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";

import { endpoints } from "./endpoints";
import type { KitchenItemResponse, OrderResponse, WaiterCallResponse } from "./types";

/*
 * Canales en tiempo real del restaurante (STOMP sobre WebSocket, sin SockJS):
 * cocina y llamadas al mesero.
 *
 * El navegador abre `/ws` en el mismo origen del frontend y `next.config.ts`
 * lo reenvia al backend, igual que `/api/v1/*`. Asi la cookie `access_token`
 * (httpOnly, SameSite=Strict) viaja sola en el handshake: el token nunca pasa
 * por JavaScript y no hace falta CORS (CAL-172).
 */

/** Si el canal se cae, el cliente reintenta con esta espera. */
export const RECONNECT_DELAY_MS = 5_000;

/**
 * Lo que publica `KitchenRealtimeEventPublisher`: la orden completa al
 * enviarse a cocina, o el producto cuyo estado cambio.
 */
export type KitchenChannelEvent = OrderResponse | KitchenItemResponse;

/** Mismo topico que `KitchenRealtimeEventPublisher.kitchenTopicFor`. */
export function kitchenTopic(tenantId: string): string {
  return `/topic/tenant.${tenantId}.kitchen`;
}

/** `ws:` o `wss:` segun la pagina, para no mezclar contenido en HTTPS. */
export function realtimeUrl(location: Pick<Location, "protocol" | "host">): string {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${location.host}${endpoints.realtime.channel()}`;
}

/** Orden a la que pertenece el evento, sea cual sea su forma. */
export function kitchenEventOrderId(event: KitchenChannelEvent): string {
  return "orderId" in event ? event.orderId : event.id;
}

export function parseKitchenEvent(message: Pick<IMessage, "body">): KitchenChannelEvent | null {
  try {
    const event = JSON.parse(message.body) as Partial<KitchenChannelEvent> | null;
    return event && typeof event.id === "string" ? (event as KitchenChannelEvent) : null;
  } catch {
    return null;
  }
}

/** Mismo topico que `WaiterCallRealtimeEventPublisher.waiterCallsTopicFor`. */
export function waiterCallsTopic(tenantId: string): string {
  return `/topic/tenant.${tenantId}.waiter-calls`;
}

export function parseWaiterCall(message: Pick<IMessage, "body">): WaiterCallResponse | null {
  try {
    const call = JSON.parse(message.body) as Partial<WaiterCallResponse> | null;
    return call && typeof call.tableId === "string" && typeof call.tableNumber === "number"
      && typeof call.calledAt === "string"
      ? (call as WaiterCallResponse)
      : null;
  } catch {
    return null;
  }
}

interface ChannelOptions<T> {
  tenantId: string;
  onEvent: (event: T) => void;
  onConnectionChange: (connected: boolean) => void;
}

interface Subscriber {
  topic: string;
  deliver: (message: IMessage) => void;
  onConnectionChange: (connected: boolean) => void;
  handle?: StompSubscription;
}

interface SharedChannel {
  client: Client;
  subscribers: Set<Subscriber>;
  connected: boolean;
}

/**
 * Una sola conexion por pestana, compartida por todas las suscripciones. El
 * plan F1 de App Service admite 5 WebSockets simultaneos para toda la app:
 * con una conexion por pantalla, la comanda del mesero abria dos (cocina y
 * llamadas) y unas cuantas pestanas agotaban el cupo.
 */
let shared: SharedChannel | null = null;

function openSharedChannel(): SharedChannel {
  const channel: SharedChannel = { client: null as unknown as Client, subscribers: new Set(), connected: false };
  channel.client = new Client({
    brokerURL: realtimeUrl(window.location),
    reconnectDelay: RECONNECT_DELAY_MS,
    onConnect: () => {
      channel.connected = true;
      for (const subscriber of channel.subscribers) {
        subscriber.handle = channel.client.subscribe(subscriber.topic, subscriber.deliver);
        subscriber.onConnectionChange(true);
      }
    },
    onWebSocketClose: () => {
      channel.connected = false;
      for (const subscriber of channel.subscribers) {
        subscriber.handle = undefined;
        subscriber.onConnectionChange(false);
      }
    },
  });
  shared = channel;
  channel.client.activate();
  return channel;
}

/**
 * Suscribe a un topico sobre la conexion compartida, abriendola si hace
 * falta. Se vuelve a suscribir en cada reconexion y descarta las tramas que
 * `parse` no reconoce. La funcion que devuelve quita la suscripcion y, si era
 * la ultima, cierra la conexion.
 */
function connectTopic<T>(
  topic: string,
  parse: (message: Pick<IMessage, "body">) => T | null,
  { onEvent, onConnectionChange }: Omit<ChannelOptions<T>, "tenantId">,
): () => void {
  const subscriber: Subscriber = {
    topic,
    deliver: (message) => {
      const event = parse(message);
      if (event) {
        onEvent(event);
      }
    },
    onConnectionChange,
  };
  const channel = shared ?? openSharedChannel();
  channel.subscribers.add(subscriber);
  if (channel.connected) {
    subscriber.handle = channel.client.subscribe(topic, subscriber.deliver);
    onConnectionChange(true);
  }

  return () => {
    onConnectionChange(false);
    if (channel.connected) {
      subscriber.handle?.unsubscribe();
    }
    channel.subscribers.delete(subscriber);
    if (channel.subscribers.size === 0) {
      if (shared === channel) {
        shared = null;
      }
      void channel.client.deactivate();
    }
  };
}

/** Canal de cocina del restaurante: ordenes enviadas y productos que cambian. */
export function connectKitchenChannel({ tenantId, ...options }: ChannelOptions<KitchenChannelEvent>): () => void {
  return connectTopic(kitchenTopic(tenantId), parseKitchenEvent, options);
}

/** Canal de llamadas al mesero del restaurante (menu QR y app de clientes). */
export function connectWaiterCallChannel({ tenantId, ...options }: ChannelOptions<WaiterCallResponse>): () => void {
  return connectTopic(waiterCallsTopic(tenantId), parseWaiterCall, options);
}
