package com.callejon9.config;

import com.callejon9.Callejon9Application;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Una migracion invalida o un esquema que no coincide con las entidades deben
 * impedir el arranque (CAL-22). Si la aplicacion llegara a arrancar, la
 * disponibilidad diria UP con una base que el codigo no sabe usar.
 *
 * <p>Ninguno de los dos casos escribe en la base: Flyway rechaza la version
 * duplicada al validar, antes de migrar, y la validacion de Hibernate solo
 * lee metadatos. Corre contra la misma base de la suite.
 */
@DisplayName("Arranque con migraciones o esquema incompatibles")
class StartupFailureTest {

    @Test
    @DisplayName("control: con migraciones y esquema validos, el registro si ve la disponibilidad en UP")
    void validStartupReachesAcceptingTraffic() {
        List<ReadinessState> readiness = new CopyOnWriteArrayList<>();

        start(readiness);

        // Sin este control, las pruebas de fallo pasarian aunque el registro
        // nunca recibiera eventos.
        assertThat(readiness).contains(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    @DisplayName("una migracion invalida detiene el arranque y la disponibilidad nunca llega a UP")
    void invalidMigrationStopsStartup() {
        List<ReadinessState> readiness = new CopyOnWriteArrayList<>();

        Throwable failure = catchThrowable(() -> start(readiness,
                "--spring.flyway.locations=classpath:db/migration,classpath:db/migration-invalida"));

        assertThat(failure).as("el arranque debia fallar").isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .contains("more than one migration with version 1");
        assertThat(readiness).doesNotContain(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    @DisplayName("un esquema que no coincide con las entidades detiene el arranque")
    void incompatibleSchemaStopsStartup() {
        List<ReadinessState> readiness = new CopyOnWriteArrayList<>();

        // Las entidades se validan contra un esquema sin tablas, como si una
        // migracion faltara.
        Throwable failure = catchThrowable(() -> start(readiness,
                "--spring.jpa.properties.hibernate.default_schema=esquema_inexistente"));

        assertThat(failure).as("el arranque debia fallar").isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                .contains("Schema-validation: missing table");
        assertThat(readiness).doesNotContain(ReadinessState.ACCEPTING_TRAFFIC);
    }

    private static void start(List<ReadinessState> readiness, String... args) {
        ApplicationListener<AvailabilityChangeEvent<?>> recorder = event -> {
            if (event.getState() instanceof ReadinessState state) {
                readiness.add(state);
            }
        };
        // Como argumentos y no como propiedades por defecto: estas tienen la
        // prioridad mas baja y application.yml ya fija spring.flyway.locations.
        new SpringApplicationBuilder(Callejon9Application.class)
                .profiles("test")
                .bannerMode(Banner.Mode.OFF)
                .listeners(recorder)
                .run(Stream.concat(Stream.of("--server.port=0"), Arrays.stream(args)).toArray(String[]::new))
                .close();
    }
}
