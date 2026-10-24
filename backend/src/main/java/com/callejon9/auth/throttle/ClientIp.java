package com.callejon9.auth.throttle;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP del navegador que origino la peticion.
 *
 * <p>El navegador nunca habla directo con el backend: Next.js reenvia
 * {@code /api/v1/*} y agrega la IP real al final de {@code X-Forwarded-For}.
 * Sin leer esa cabecera, todas las peticiones parecerian venir del servidor de
 * Next y el limite por IP bloquearia a todos los usuarios a la vez.
 *
 * <p>Pero cualquiera puede escribir la cabecera, asi que solo se le cree
 * cuando la conexion viene de la red local (loopback o privada), que es donde
 * vive Next. Y se toma la ultima entrada, la que agrego el proxy, no la
 * primera, que el cliente pudo inventar. Si la peticion llega de una IP
 * publica, se usa la direccion de la conexion y la cabecera se ignora.
 *
 * <p>Azure App Service escribe cada entrada como {@code ip:puerto}. El puerto
 * cambia en cada conexion, asi que se descarta: con el, un mismo cliente
 * tendria una llave distinta por peticion y el limite por IP no lo frenaria.
 */
public final class ClientIp {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR);

        if (forwardedFor == null || forwardedFor.isBlank() || !isInternal(remoteAddress)) {
            return remoteAddress;
        }
        String[] hops = forwardedFor.split(",");
        String last = withoutPort(hops[hops.length - 1].strip());
        return last.isEmpty() ? remoteAddress : last;
    }

    /**
     * Quita el puerto de una entrada: {@code 1.2.3.4:5678} y {@code [2001:db8::1]:443}.
     * Una IPv6 sin corchetes tiene varios {@code :} y se deja tal cual.
     */
    private static String withoutPort(String hop) {
        if (hop.startsWith("[")) {
            int end = hop.indexOf(']');
            return end > 1 ? hop.substring(1, end) : "";
        }
        int colon = hop.indexOf(':');
        if (colon >= 0 && colon == hop.lastIndexOf(':')) {
            return hop.substring(0, colon);
        }
        return hop;
    }

    private static boolean isInternal(String address) {
        try {
            // getRemoteAddr devuelve una IP literal: no hay consulta DNS.
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
