package com.callejon9.order.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Genera folios con el formato {@code ORD-yyMMddHHmmss} en UTC, el mismo
 * esquema que usaba {@code COM-} en el sistema Flask original.
 *
 * <p>Formatear directamente {@code Instant.now()} no basta: dos ordenes
 * abiertas en el mismo segundo (facil en hora pico, con varios meseros
 * atendiendo a la vez) producirian el mismo folio y la restriccion
 * {@code UNIQUE (tenant_id, folio)} rechazaria la segunda con un error de
 * base de datos poco amigable. En vez de esperar a que eso no pase, este
 * generador mantiene el ULTIMO segundo emitido en un {@link AtomicLong} y
 * garantiza, con una operacion atomica, que cada folio nuevo use un segundo
 * estrictamente mayor al anterior — nunca repetido dentro de esta instancia
 * de la aplicacion, sin reintentos ni bloqueos de base de datos.
 */
@Component
public class FolioGenerator {

    private static final String PREFIX = "ORD-";
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final AtomicLong lastIssuedEpochSecond = new AtomicLong(0);

    public String next() {
        long now = Instant.now().getEpochSecond();
        long issued = lastIssuedEpochSecond.updateAndGet(previous -> Math.max(previous + 1, now));
        return PREFIX + FORMAT.format(Instant.ofEpochSecond(issued));
    }
}
