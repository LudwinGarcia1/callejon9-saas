package com.callejon9.shared.observability;

import io.sentry.SentryEvent;
import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Aplica {@link SentryRedaction} a todo lo que se envia a Sentry: el cuerpo y
 * los atributos de texto de cada log, y el mensaje y las excepciones de cada
 * evento de error. La autoconfiguracion de Sentry recoge estos beans.
 */
@Configuration(proxyBeanMethods = false)
public class SentryPrivacyConfig {

    @Bean
    SentryOptions.Logs.BeforeSendLogCallback redactSentryLogs() {
        return SentryPrivacyConfig::redactLog;
    }

    @Bean
    SentryOptions.BeforeSendCallback redactSentryEvents() {
        return (event, hint) -> redactEvent(event);
    }

    static SentryLogEvent redactLog(SentryLogEvent log) {
        log.setBody(SentryRedaction.redact(log.getBody()));
        Map<String, SentryLogEventAttributeValue> attributes = log.getAttributes();
        if (attributes != null) {
            attributes.replaceAll((key, value) -> value != null && value.getValue() instanceof String text
                    ? new SentryLogEventAttributeValue(value.getType(), SentryRedaction.redact(text))
                    : value);
        }
        return log;
    }

    static SentryEvent redactEvent(SentryEvent event) {
        Message message = event.getMessage();
        if (message != null) {
            message.setFormatted(SentryRedaction.redact(message.getFormatted()));
            message.setMessage(SentryRedaction.redact(message.getMessage()));
            List<String> params = message.getParams();
            if (params != null) {
                message.setParams(params.stream().map(SentryRedaction::redact).toList());
            }
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException exception : exceptions) {
                exception.setValue(SentryRedaction.redact(exception.getValue()));
            }
        }
        return event;
    }
}
