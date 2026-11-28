import { WaiterCallsProvider } from "@/hooks/use-waiter-calls";
import { WaiterCallAlerts } from "./waiter-call-alerts";

/**
 * Comun a la cuadricula de mesas y a la comanda: el canal de llamadas sigue
 * abierto al pasar de una a otra, y la franja de llamadas se ve en ambas.
 */
export default function WaiterLayout({ children }: { children: React.ReactNode }) {
  return (
    <WaiterCallsProvider>
      <WaiterCallAlerts />
      {children}
    </WaiterCallsProvider>
  );
}
