"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { toast } from "sonner";

import { useSession } from "@/hooks/use-session";
import { formatShortTime } from "@/lib/format";
import { connectWaiterCallChannel } from "@/lib/realtime";
import type { WaiterCallResponse } from "@/lib/types";

/** Lo que dura el aviso emergente; la llamada sigue en la franja hasta atenderla. */
export const WAITER_CALL_TOAST_MS = 15_000;

interface WaiterCallsValue {
  /** Llamadas sin atender, la mas reciente primero; una por mesa. */
  calls: WaiterCallResponse[];
  connected: boolean;
  dismiss: (tableId: string) => void;
}

const WaiterCallsContext = createContext<WaiterCallsValue>({
  calls: [],
  connected: false,
  dismiss: () => {},
});

function toastId(tableId: string): string {
  return `waiter-call-${tableId}`;
}

/**
 * Escucha las llamadas al mesero del restaurante de la sesion y las comparte
 * con las pantallas del mesero. Cada llamada nueva muestra un aviso
 * emergente y queda en la lista hasta que alguien la marca como atendida.
 *
 * La lista vive en memoria de esta pestana: una llamada hecha mientras el
 * mesero no tenia la pantalla abierta no aparece despues.
 */
export function WaiterCallsProvider({ children }: { children: ReactNode }) {
  const { user } = useSession();
  const tenantId = user?.tenantId;
  const [calls, setCalls] = useState<WaiterCallResponse[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!tenantId) {
      return;
    }
    return connectWaiterCallChannel({
      tenantId,
      onEvent: (call) => {
        setCalls((current) => [call, ...current.filter((pending) => pending.tableId !== call.tableId)]);
        toast.warning(`Mesa ${call.tableNumber} llama al mesero`, {
          id: toastId(call.tableId),
          description: `Llamó a las ${formatShortTime(call.calledAt)}.`,
          duration: WAITER_CALL_TOAST_MS,
        });
      },
      onConnectionChange: setConnected,
    });
  }, [tenantId]);

  const dismiss = useCallback((tableId: string) => {
    setCalls((current) => current.filter((call) => call.tableId !== tableId));
    toast.dismiss(toastId(tableId));
  }, []);

  const value = useMemo(() => ({ calls, connected, dismiss }), [calls, connected, dismiss]);
  return <WaiterCallsContext.Provider value={value}>{children}</WaiterCallsContext.Provider>;
}

export function useWaiterCalls(): WaiterCallsValue {
  return useContext(WaiterCallsContext);
}
