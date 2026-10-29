/**
 * IP del navegador para los limites por IP del backend (CAL-169).
 *
 * En Azure, el front end de App Service agrega la IP del navegador al final de
 * `X-Forwarded-For` antes de entregar la peticion a Next. El backend no puede
 * leerla de esa cadena: llega despues de la IP de salida de este frontend, que
 * no es fija ni exclusiva. Por eso el middleware la reenvia en una cabecera
 * propia junto con un secreto que solo conocen Next y la API.
 *
 * Solo tiene sentido detras de un proxy que agregue esa entrada. Sin uno (Next
 * expuesto directo, como en Compose), la cadena la escribe el navegador; por
 * eso no se configura `BACKEND_PROXY_SECRET` en ese caso.
 */

/** Cabecera con la IP del navegador; el backend la lee solo si llega el secreto. */
export const CLIENT_IP_HEADER = "x-callejon9-client-ip";

/** Secreto compartido con la API (`APP_PROXY_SECRET`). Nunca sale hacia el navegador. */
export const PROXY_SECRET_HEADER = "x-callejon9-proxy-secret";

const IPV4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;
const IPV6 = /^(?=.*:)[0-9A-Fa-f:][0-9A-Fa-f:.]*$/;

/**
 * Ultima entrada de `X-Forwarded-For`, sin puerto: `1.2.3.4:5678` y
 * `[2001:db8::1]:443`. Las anteriores las pudo escribir el navegador y no se
 * leen. Devuelve `null` si la entrada no es una IP literal.
 */
export function browserIpFromForwardedFor(forwardedFor: string | null): string | null {
  if (!forwardedFor) {
    return null;
  }
  const hops = forwardedFor.split(",");
  const address = withoutPort(hops[hops.length - 1].trim());
  return IPV4.test(address) || IPV6.test(address) ? address : null;
}

function withoutPort(hop: string): string {
  if (hop.startsWith("[")) {
    const end = hop.indexOf("]");
    return end > 1 ? hop.slice(1, end) : "";
  }
  const colon = hop.indexOf(":");
  return colon >= 0 && colon === hop.lastIndexOf(":") ? hop.slice(0, colon) : hop;
}

/**
 * Cabeceras para reenviar una peticion de `/api/v1/*` al backend. Siempre
 * quita las dos cabeceras propias que mande el navegador; las vuelve a poner
 * solo con un secreto configurado y una IP valida.
 */
export function apiRequestHeaders(incoming: Headers, proxySecret: string | undefined): Headers {
  const headers = new Headers(incoming);
  headers.delete(CLIENT_IP_HEADER);
  headers.delete(PROXY_SECRET_HEADER);

  const secret = proxySecret?.trim();
  const browserIp = browserIpFromForwardedFor(incoming.get("x-forwarded-for"));
  if (secret && browserIp) {
    headers.set(CLIENT_IP_HEADER, browserIp);
    headers.set(PROXY_SECRET_HEADER, secret);
  }
  return headers;
}
