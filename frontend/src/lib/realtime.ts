import { Client, type IMessage } from "@stomp/stompjs";

import { endpoints } from "./endpoints";
import type { KitchenItemResponse, OrderResponse } from "./types";

/*
 * Canal en tiempo real de cocina (STOMP sobre WebSocket, sin SockJS).
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

interface KitchenChannelOptions {
  tenantId: string;
  onEvent: (event: KitchenChannelEvent) => void;
  onConnectionChange: (connected: boolean) => void;
}

/**
 * Crea y activa el cliente. Se suscribe al topico del restaurante en cada
 * conexion, incluidas las reconexiones. Devuelve la funcion que lo cierra.
 */
export function connectKitchenChannel({
  tenantId,
  onEvent,
  onConnectionChange,
}: KitchenChannelOptions): () => void {
  const client = new Client({
    brokerURL: realtimeUrl(window.location),
    reconnectDelay: RECONNECT_DELAY_MS,
    onConnect: () => {
      onConnectionChange(true);
      client.subscribe(kitchenTopic(tenantId), (message) => {
        const event = parseKitchenEvent(message);
        if (event) {
          onEvent(event);
        }
      });
    },
    onWebSocketClose: () => onConnectionChange(false),
  });
  client.activate();

  return () => {
    onConnectionChange(false);
    void client.deactivate();
  };
}
