package com.callejon9.shared.observability;

import io.sentry.Sentry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Metricas de negocio que se envian a Sentry.
 *
 * <p>Solo son conteos, sin montos ni identificadores de restaurante: las cifras
 * de venta de cada restaurante no salen hacia un servicio externo. Dentro de
 * una transaccion, el conteo se registra al confirmarse, para no contar una
 * operacion que termino en rollback. Sin SENTRY_DSN, Sentry no esta
 * inicializado y las llamadas no hacen nada.
 */
public final class BusinessMetrics {

    static final String ORDERS_OPENED = "orders_opened";
    static final String ORDERS_SENT_TO_KITCHEN = "orders_sent_to_kitchen";
    static final String CHECKOUTS_COMPLETED = "checkouts_completed";
    static final String LOGIN_FAILURES = "login_failures";

    private BusinessMetrics() {
    }

    public static void orderOpened() {
        countAfterCommit(ORDERS_OPENED);
    }

    public static void orderSentToKitchen() {
        countAfterCommit(ORDERS_SENT_TO_KITCHEN);
    }

    public static void checkoutCompleted() {
        countAfterCommit(CHECKOUTS_COMPLETED);
    }

    /** Un login rechazado; se cuenta de inmediato porque no hay nada que confirmar. */
    public static void loginFailed() {
        count(LOGIN_FAILURES);
    }

    static void countAfterCommit(String name) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    count(name);
                }
            });
        } else {
            count(name);
        }
    }

    private static void count(String name) {
        Sentry.metrics().count(name, 1.0);
    }
}
