package com.callejon9.shared.observability;

import io.sentry.SentryAttributeType;
import io.sentry.SentryEvent;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryLogLevel;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Privacidad de lo que se envia a Sentry")
class SentryPrivacyConfigTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJh";

    @Test
    @DisplayName("tacha correos y JWT y deja el resto del texto")
    void redactsEmailsAndTokens() {
        assertThat(SentryRedaction.redact("Key (email)=(ana.lopez@restaurante.mx) already exists"))
                .isEqualTo("Key (email)=([correo]) already exists");
        assertThat(SentryRedaction.redact("token " + JWT + " rechazado"))
                .isEqualTo("token [jwt] rechazado");
        assertThat(SentryRedaction.redact("Orden A-12 enviada a cocina"))
                .isEqualTo("Orden A-12 enviada a cocina");
        assertThat(SentryRedaction.redact(null)).isNull();
    }

    @Test
    @DisplayName("tacha el cuerpo y los atributos de texto de un log")
    void redactsLogs() {
        SentryLogEvent log = new SentryLogEvent(SentryId.EMPTY_ID, 0.0,
                "Se reemplazo la contrasena del super-administrador admin@callejon9.mx", SentryLogLevel.WARN);
        log.setAttribute("message.parameter.0",
                new SentryLogEventAttributeValue(SentryAttributeType.STRING, "admin@callejon9.mx"));
        log.setAttribute("count", new SentryLogEventAttributeValue(SentryAttributeType.INTEGER, 3));

        SentryLogEvent sent = SentryPrivacyConfig.redactLog(log);

        assertThat(sent.getBody()).isEqualTo("Se reemplazo la contrasena del super-administrador [correo]");
        assertThat(sent.getAttributes().get("message.parameter.0").getValue()).isEqualTo("[correo]");
        assertThat(sent.getAttributes().get("count").getValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("tacha el mensaje y las excepciones de un evento de error")
    void redactsEvents() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setFormatted("Fallo el alta de ana@restaurante.mx");
        message.setParams(List.of("ana@restaurante.mx"));
        event.setMessage(message);
        SentryException exception = new SentryException();
        exception.setValue("duplicate key: (email)=(ana@restaurante.mx)");
        event.setExceptions(List.of(exception));

        SentryEvent sent = SentryPrivacyConfig.redactEvent(event);

        assertThat(sent.getMessage().getFormatted()).isEqualTo("Fallo el alta de [correo]");
        assertThat(sent.getMessage().getParams()).containsExactly("[correo]");
        assertThat(sent.getExceptions().get(0).getValue()).isEqualTo("duplicate key: (email)=([correo])");
    }
}
