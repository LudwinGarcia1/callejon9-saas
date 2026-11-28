package com.callejon9.order.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limita los avisos de «Llamar al mesero» a uno por mesa cada
 * {@link #WINDOW}. El endpoint es publico: sin este limite, tocar el boton
 * repetidas veces (o un script) llenaria de avisos la pantalla de cada
 * mesero del restaurante.
 *
 * <p>La llamada repetida dentro de la ventana no es un error para el
 * comensal: el personal ya recibio el aviso de esa mesa. El estado vive en
 * memoria de esta instancia; con varias instancias el limite es por
 * instancia, lo que sigue acotando el ruido.
 */
@Component
public class WaiterCallThrottle {

    public static final Duration WINDOW = Duration.ofSeconds(20);

    /** Por encima de este tamano se descartan las entradas vencidas. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final Map<String, Instant> lastCallByTable = new ConcurrentHashMap<>();

    /**
     * Devuelve {@code true} si la llamada de esa mesa debe avisarse ahora, y
     * la registra; {@code false} si la mesa ya llamo dentro de la ventana.
     */
    public boolean tryAcquire(UUID tenantId, int tableNumber, Instant now) {
        if (lastCallByTable.size() > PURGE_THRESHOLD) {
            lastCallByTable.values().removeIf(last -> !now.isBefore(last.plus(WINDOW)));
        }
        String key = tenantId + ":" + tableNumber;
        boolean[] acquired = {false};
        lastCallByTable.compute(key, (k, last) -> {
            if (last != null && now.isBefore(last.plus(WINDOW))) {
                return last;
            }
            acquired[0] = true;
            return now;
        });
        return acquired[0];
    }
}
