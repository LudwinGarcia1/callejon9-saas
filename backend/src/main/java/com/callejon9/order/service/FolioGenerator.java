package com.callejon9.order.service;

import com.callejon9.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera folios con el formato {@code ORD-yyMMddHHmmss} o {@code TCK-yyMMddHHmmss} en UTC.
 *
 * <p>En un entorno multi-instancia (por ejemplo, varias tareas ECS en ejecucion concurrente),
 * la generacion en memoria de una sola JVM no puede prevenir colisiones si dos instancias
 * reciben solicitudes en el mismo segundo para el mismo restaurante.
 *
 * <p>Esta implementacion utiliza PostgreSQL como fuente de verdad mediante la tabla
 * {@code tenant_folio_sequences}. Una operacion atomica {@code INSERT ... ON CONFLICT DO UPDATE}
 * con {@code GREATEST(tenant_folio_sequences.last_issued_epoch_second + 1, EXCLUDED.last_issued_epoch_second)}
 * garantiza que cada folio asignado sea estrictamente mayor al anterior y unico por restaurante,
 * sin carreras entre instancias.
 *
 * <p>Si no hay conexion a base de datos o contexto de tenant (por ejemplo, en pruebas unitarias
 * puras sin contexto de Spring), recurre a un generador en memoria con {@link AtomicLong}
 * para preservar compatibilidad.
 */
@Component
public class FolioGenerator {

    public static final String ORDER_PREFIX = "ORD-";
    public static final String TICKET_PREFIX = "TCK-";

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final AtomicLong fallbackEpochSecond = new AtomicLong(0);

    public FolioGenerator() {
        this(null, Clock.systemUTC());
    }

    @Autowired
    public FolioGenerator(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    public FolioGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String next() {
        return next(ORDER_PREFIX);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String next(String prefix) {
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId != null && jdbcTemplate != null) {
            return next(prefix, tenantId);
        }
        return inMemoryNext(prefix);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String next(String prefix, UUID tenantId) {
        if (jdbcTemplate == null || tenantId == null) {
            return inMemoryNext(prefix);
        }

        long now = clock.instant().getEpochSecond();
        Long issued = jdbcTemplate.queryForObject(
                """
                INSERT INTO tenant_folio_sequences (tenant_id, prefix, last_issued_epoch_second, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (tenant_id, prefix)
                DO UPDATE SET
                    last_issued_epoch_second = GREATEST(tenant_folio_sequences.last_issued_epoch_second + 1, EXCLUDED.last_issued_epoch_second),
                    updated_at = now()
                RETURNING last_issued_epoch_second
                """,
                Long.class,
                tenantId,
                prefix,
                now);

        long epochSecond = Objects.requireNonNullElse(issued, now);
        return prefix + FORMAT.format(Instant.ofEpochSecond(epochSecond));
    }

    private String inMemoryNext(String prefix) {
        long now = clock.instant().getEpochSecond();
        long issued = fallbackEpochSecond.updateAndGet(previous -> Math.max(previous + 1, now));
        return prefix + FORMAT.format(Instant.ofEpochSecond(issued));
    }
}
