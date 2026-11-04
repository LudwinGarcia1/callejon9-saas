"use client";

import { useEffect, useRef, useState } from "react";

import { useSession } from "@/hooks/use-session";
import { connectKitchenChannel, type KitchenChannelEvent } from "@/lib/realtime";

/**
 * Escucha el canal en tiempo real de cocina del restaurante de la sesion.
 *
 * `connected` indica si el canal esta abierto, para que la pantalla espacie
 * su consulta periodica mientras lo este. La consulta nunca se apaga: si el
 * canal se cae, la pantalla sigue al dia por ella (CAL-172).
 */
export function useKitchenChannel(onEvent: (event: KitchenChannelEvent) => void) {
  const { user } = useSession();
  const tenantId = user?.tenantId;
  const [connected, setConnected] = useState(false);
  const onEventRef = useRef(onEvent);

  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!tenantId) {
      return;
    }
    return connectKitchenChannel({
      tenantId,
      onEvent: (event) => onEventRef.current(event),
      onConnectionChange: setConnected,
    });
  }, [tenantId]);

  return { connected };
}
