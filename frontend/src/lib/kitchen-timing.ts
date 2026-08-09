/** Umbrales del semaforo de cocina, en minutos desde que la comanda se envio. */
const WARNING_AFTER_MINUTES = 15;
const CRITICAL_AFTER_MINUTES = 25;

export type OrderAge = "normal" | "warning" | "critical";

/**
 * Que tan urgente es una comanda por el tiempo que lleva esperando.
 *
 * `now` entra como parametro y no se lee aqui con Date.now() para que el
 * resultado sea reproducible: el componente decide cada cuanto avanza el
 * reloj, y esta funcion siempre da la misma respuesta para las mismas
 * entradas.
 */
export function orderAge(sentAt: string | null, now: number): OrderAge {
  if (!sentAt) {
    return "normal";
  }

  const minutes = (now - new Date(sentAt).getTime()) / 60_000;

  if (minutes >= CRITICAL_AFTER_MINUTES) {
    return "critical";
  }
  if (minutes >= WARNING_AFTER_MINUTES) {
    return "warning";
  }
  return "normal";
}

/**
 * Cuanto lleva esperando, en palabras. A metro y medio de distancia nadie
 * resta una hora contra el reloj de la pared, que es lo que la pantalla
 * pedia hasta ahora al mostrar "enviada 16:44".
 */
export function elapsedLabel(sentAt: string | null, now: number): string {
  if (!sentAt) {
    return "";
  }

  const minutes = Math.floor((now - new Date(sentAt).getTime()) / 60_000);

  if (minutes < 1) {
    return "recién enviada";
  }
  return `hace ${minutes} min`;
}
