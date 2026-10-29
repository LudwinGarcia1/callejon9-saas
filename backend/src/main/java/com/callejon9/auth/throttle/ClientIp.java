package com.callejon9.auth.throttle;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP del navegador que origino la peticion, la llave de los limites por IP del
 * login y del alta de restaurantes.
 *
 * <p>El backend casi nunca recibe la conexion del navegador. Delante hay uno o
 * varios proxies que agregan al final de {@code X-Forwarded-For} la direccion
 * de quien se les conecto:
 *
 * <ul>
 *   <li><b>Compose:</b> navegador → Next → backend.</li>
 *   <li><b>Azure, directo a la API:</b> cliente → front end de App Service →
 *       contenedor. La conexion llega desde una direccion de enlace local
 *       (169.254.x.x), comun a todas las peticiones.</li>
 *   <li><b>Azure, por el frontend:</b> navegador → App Service del frontend →
 *       Next → App Service de la API → contenedor. El ultimo salto de la
 *       cabecera es la IP de salida del frontend, comun a todos sus usuarios.</li>
 * </ul>
 *
 * <p>Por eso la cadena se recorre de derecha a izquierda saltando los proxies
 * de confianza, y la primera direccion que no lo es se toma como el cliente.
 * Las entradas a su izquierda las pudo escribir el cliente y nunca se leen.
 * Son de confianza la red local (loopback, privada y de enlace local, que no
 * se alcanzan desde internet) y las direcciones de
 * {@code app.client-ip.trusted-proxies}, donde va la IP de salida del frontend.
 * Si la conexion misma no viene de un proxy de confianza, la cabecera se ignora.
 *
 * <p>Azure App Service escribe cada entrada como {@code ip:puerto}. El puerto
 * cambia en cada conexion, asi que se descarta: con el, un mismo cliente
 * tendria una llave distinta por peticion y el limite por IP no lo frenaria.
 *
 * <p>La lista de IP de salida no basta en Azure: el frontend puede salir por
 * una direccion que no esta en ella, y en el plan F1 esas direcciones las
 * comparten otras apps. Por eso el middleware de Next, que lee la IP del
 * navegador de la cabecera que escribe su propio front end de App Service,
 * la reenvia en {@code X-Callejon9-Client-Ip} junto con el secreto
 * {@code app.client-ip.proxy-secret} (CAL-169). Si el secreto coincide, esa
 * IP es la llave y la cadena de {@code X-Forwarded-For} no se consulta. Sin
 * secreto configurado, o con uno que no coincide, ambas cabeceras se ignoran.
 */
@Component
public class ClientIp {

    static final String FORWARDED_FOR = "X-Forwarded-For";
    static final String PROXY_CLIENT_IP = "X-Callejon9-Client-Ip";
    static final String PROXY_SECRET = "X-Callejon9-Proxy-Secret";

    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    /** Empieza como un literal y lleva {@code :}: Java lo parsea como IPv6 o falla, sin DNS. */
    private static final Pattern IPV6 = Pattern.compile("(?=.*:)[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    private final List<TrustedRange> trustedProxies;
    /** Vacio cuando no hay secreto: entonces nunca se lee {@link #PROXY_CLIENT_IP}. */
    private final byte[] proxySecret;

    public ClientIp(List<String> trustedProxies) {
        this(trustedProxies, "");
    }

    @Autowired
    public ClientIp(@Value("${app.client-ip.trusted-proxies:}") List<String> trustedProxies,
                    @Value("${app.client-ip.proxy-secret:}") String proxySecret) {
        this.trustedProxies = trustedProxies.stream()
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .map(TrustedRange::parse)
                .toList();
        this.proxySecret = proxySecret.strip().getBytes(StandardCharsets.UTF_8);
    }

    public String of(HttpServletRequest request) {
        Optional<String> fromFrontend = fromAuthenticatedFrontend(request);
        if (fromFrontend.isPresent()) {
            return fromFrontend.get();
        }

        String remoteAddress = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR);

        if (forwardedFor == null || forwardedFor.isBlank()
                || !literal(remoteAddress).map(this::isTrusted).orElse(false)) {
            return remoteAddress;
        }
        String client = remoteAddress;
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = withoutPort(hops[i].strip());
            Optional<InetAddress> address = literal(hop);
            if (address.isEmpty()) {
                // Entrada vacia o que no es una IP: lo que siga a la izquierda
                // ya no se puede atribuir a un proxy. Cuenta el ultimo salto valido.
                return client;
            }
            client = hop;
            if (!isTrusted(address.get())) {
                return hop;
            }
        }
        return client;
    }

    /**
     * IP que reenvio el middleware de Next, solo si trae el secreto compartido.
     * Se compara en tiempo constante; una IP ausente o mal formada cae a la
     * resolucion por {@code X-Forwarded-For}.
     */
    private Optional<String> fromAuthenticatedFrontend(HttpServletRequest request) {
        String presented = request.getHeader(PROXY_SECRET);
        if (proxySecret.length == 0 || presented == null
                || !MessageDigest.isEqual(proxySecret, presented.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String clientIp = request.getHeader(PROXY_CLIENT_IP);
        if (clientIp == null) {
            return Optional.empty();
        }
        String address = withoutPort(clientIp.strip());
        return literal(address).map(ignored -> address);
    }

    private boolean isTrusted(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || trustedProxies.stream().anyMatch(range -> range.contains(address));
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

    /**
     * Solo interpreta literales IPv4 o IPv6. La cabecera la escribe el cliente:
     * un nombre de host en ella no debe disparar una consulta DNS.
     */
    private static Optional<InetAddress> literal(String text) {
        if (!IPV4.matcher(text).matches() && !IPV6.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(text));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    /** Una IP o un rango CIDR ({@code 20.1.2.3}, {@code 20.1.2.0/24}, {@code 2001:db8::/32}). */
    private record TrustedRange(byte[] network, int prefixLength) {

        static TrustedRange parse(String entry) {
            int slash = entry.indexOf('/');
            String base = slash < 0 ? entry : entry.substring(0, slash);
            byte[] network = literal(base)
                    .map(InetAddress::getAddress)
                    .orElseThrow(() -> invalid(entry));
            int maxLength = network.length * 8;
            int prefixLength;
            try {
                prefixLength = slash < 0 ? maxLength : Integer.parseInt(entry.substring(slash + 1));
            } catch (NumberFormatException exception) {
                throw invalid(entry);
            }
            if (prefixLength < 0 || prefixLength > maxLength) {
                throw invalid(entry);
            }
            return new TrustedRange(network, prefixLength);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        private static IllegalArgumentException invalid(String entry) {
            return new IllegalArgumentException(
                    "app.client-ip.trusted-proxies: '" + entry + "' no es una IP ni un rango CIDR");
        }
    }
}
