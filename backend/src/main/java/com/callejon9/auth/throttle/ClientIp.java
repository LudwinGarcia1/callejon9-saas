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
        String last = hops[hops.length - 1].strip();
        return last.isEmpty() ? remoteAddress : last;
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
