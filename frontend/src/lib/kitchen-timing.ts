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

const MINUTES_PER_HOUR = 60;

/**
 * Cuanto lleva esperando, en palabras. A metro y medio de distancia nadie
 * resta una hora contra el reloj de la pared, que es lo que la pantalla
 * pedia hasta ahora al mostrar "enviada 16:44".
 *
 * Pasa a horas al llegar a los 60 minutos por la misma razon: "hace 137 min"
 * es esa misma resta disfrazada, y cae justo sobre la comanda olvidada que el
 * umbral critico existe para hacer saltar a la vista.
 */
export function elapsedLabel(sentAt: string | null, now: number): string {
  if (!sentAt) {
    return "";
  }

  const minutes = Math.floor((now - new Date(sentAt).getTime()) / 60_000);

  if (minutes < 1) {
    return "recién enviada";
  }
  if (minutes < MINUTES_PER_HOUR) {
    return `hace ${minutes} min`;
  }

  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  const remainder = minutes % MINUTES_PER_HOUR;

  return remainder === 0 ? `hace ${hours} h` : `hace ${hours} h ${remainder} min`;
}
