package com.callejon9.config;

import io.sentry.spring.boot.jakarta.SentryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuracion de Sentry en application.yml, sin levantar el contexto.
 *
 * <p>Protege dos reglas: sin SENTRY_DSN el SDK queda apagado (CI y pruebas no
 * envian nada) y ningun evento lleva cookies, cabeceras de sesion ni el cuerpo
 * de la peticion, que es donde viajan el JWT y los datos del restaurante.
 */
@DisplayName("Configuracion de Sentry")
class SentryConfigurationTest {

    private static SentryProperties bind() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return Binder.get(environment).bind("sentry", SentryProperties.class).get();
    }

    @Test
    @DisplayName("sin SENTRY_DSN el DSN queda vacio y el SDK no se activa")
    void disabledWithoutDsn() throws IOException {
        assertThat(bind().getDsn()).isNullOrEmpty();
    }

    @Test
    @DisplayName("no envia PII ni el cuerpo de las peticiones")
    void sendsNoPersonalData() throws IOException {
        SentryProperties properties = bind();

        assertThat(properties.isSendDefaultPii()).isFalse();
        assertThat(properties.getMaxRequestBodySize())
                .isEqualTo(io.sentry.SentryOptions.RequestSize.NONE);
    }

    @Test
    @DisplayName("traza una de cada diez peticiones por defecto")
    void tracesTenPercentByDefault() throws IOException {
        assertThat(bind().getTracesSampleRate()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("envia los logs de INFO en adelante a Sentry Logs")
    void sendsLogsFromInfo() throws IOException {
        SentryProperties properties = bind();

        assertThat(properties.getLogs().isEnabled()).isTrue();
        assertThat(properties.getLogging().getMinimumLevel()).hasToString("INFO");
    }
}
