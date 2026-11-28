package com.callejon9.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Metricas de negocio")
class BusinessMetricsTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("dentro de una transaccion espera al commit para contar")
    void defersCountUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        BusinessMetrics.checkoutCompleted();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("sin Sentry inicializado contar no falla")
    void countingWithoutSentryIsNoop() {
        assertThatCode(BusinessMetrics::loginFailed).doesNotThrowAnyException();
        assertThatCode(BusinessMetrics::orderOpened).doesNotThrowAnyException();
    }
}
