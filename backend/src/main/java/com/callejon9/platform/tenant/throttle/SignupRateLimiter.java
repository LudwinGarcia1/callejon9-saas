package com.callejon9.platform.tenant.throttle;

import com.callejon9.shared.throttle.RateLimitExceededException;
import com.callejon9.shared.throttle.SlidingWindowRateLimiter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Freno contra altas masivas en el registro publico de restaurantes. Cada alta
 * crea un restaurante, su suscripcion y su administrador, y calcula un bcrypt;
 * sin cupo, un solo cliente podria crear restaurantes sin limite y acaparar
 * identificadores.
 *
 * <p>La llave sale de la IP del cliente, resuelta con
 * {@link com.callejon9.auth.throttle.ClientIp} igual que en el login. Cuenta
 * toda alta que llega al servicio, termine creada o con el slug ocupado: si un
 * 409 no contara, el endpoint serviria para enumerar restaurantes sin freno.
 */
@Component
public class SignupRateLimiter {

    static final String TITLE = "Demasiadas altas";

    /** Bytes del prefijo de red de IPv6 que identifican a un cliente (/64). */
    private static final int IPV6_CLIENT_PREFIX_BYTES = 8;

    private final SlidingWindowRateLimiter limiter;

    public SignupRateLimiter(
            @Value("${app.signup.rate-limit.max-per-ip}") int maxPerIp,
            @Value("${app.signup.rate-limit.window}") Duration window) {
        this.limiter = new SlidingWindowRateLimiter(maxPerIp, window, Clock.systemUTC());
    }

    /**
     * Registra el alta de {@code clientIp} o, si ya agoto su cupo, lanza
     * {@link RateLimitExceededException} sin registrar nada.
     */
    public void acquire(String clientIp) {
        Duration wait = limiter.tryAcquire(keyFor(clientIp));
        if (!wait.isZero()) {
            throw new RateLimitExceededException(TITLE,
                    "Se alcanzo el limite de registros de restaurantes desde esta conexion.", wait);
        }
    }

    /**
     * Llave de cupo para una IP. Una IPv4 cuenta tal cual. Una IPv6 cuenta por
     * su red /64: un proveedor entrega esa red completa a cada cliente, que
     * puede estrenar una direccion en cada peticion; contar por direccion
     * dejaria el limite sin efecto. Una IPv6 que encapsula una IPv4
     * ({@code ::ffff:a.b.c.d}) cuenta como esa IPv4.
     *
     * <p>Solo se interpretan literales IPv6 (con dos puntos), que no generan
     * consulta DNS. Cualquier otro valor se usa tal cual.
     */
    static String keyFor(String clientIp) {
        if (clientIp == null || clientIp.indexOf(':') < 0) {
            return clientIp;
        }
        try {
            InetAddress address = InetAddress.getByName(clientIp);
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                return address.getHostAddress();
            }
            byte[] network = Arrays.copyOf(
                    Arrays.copyOf(bytes, IPV6_CLIENT_PREFIX_BYTES), bytes.length);
            return InetAddress.getByAddress(network).getHostAddress() + "/64";
        } catch (UnknownHostException notAnAddress) {
            return clientIp;
        }
    }
}
