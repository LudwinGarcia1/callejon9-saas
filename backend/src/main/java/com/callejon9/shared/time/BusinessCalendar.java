package com.callejon9.shared.time;

import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import java.time.Clock;
import java.time.ZoneId;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Entrega el calendario del restaurante activo: "hoy" y los limites de un
 * rango de dias en SU zona horaria, no en UTC ni en una zona global.
 *
 * Con Mexico City en -06:00, resolver un dia en UTC lo corta a la mitad: a
 * partir de las 18:00 locales UTC ya paso al dia siguiente. Cualquier
 * agregacion "por dia" (historial de ventas, reportes) que ignore esto
 * termina partiendo un servicio de cena en dos filas o mostrando la cena
 * equivocada. Ver SaleHistoryService para el caso que origino esta clase.
 *
 * La zona es del tenant (CAL-19): se lee de {@code tenants.timezone} en cada
 * llamada a {@link #current()}, para el tenant de {@link TenantContext}. No
 * hay cache: un singleton que recordara la zona del ultimo tenant atendido
 * cortaria el dia de un restaurante con la medianoche de otro.
 */
@Component
public class BusinessCalendar {

    private final Supplier<ZoneId> currentZone;
    private final Clock clock;

    @Autowired
    public BusinessCalendar(TenantRepository tenantRepository) {
        this(() -> ZoneId.of(tenantRepository.findTimezoneById(TenantContext.require())
                .orElseThrow(() -> new IllegalStateException("El restaurante activo no existe."))),
                Clock.systemUTC());
    }

    BusinessCalendar(Supplier<ZoneId> currentZone, Clock clock) {
        this.currentZone = currentZone;
        this.clock = clock;
    }

    /** Calendario fijo en una zona, sin consultar la base: para pruebas unitarias de los servicios. */
    public static BusinessCalendar fixed(ZoneId zone) {
        return new BusinessCalendar(() -> zone, Clock.systemUTC());
    }

    /**
     * Calendario del restaurante activo. Se resuelve una vez por operacion y
     * se reutiliza para "hoy", el rango y la agregacion SQL, de modo que las
     * tres usen la misma zona aunque un ADMIN la cambie a media peticion.
     */
    public TenantCalendar current() {
        return new TenantCalendar(currentZone.get(), clock);
    }
}
