package com.callejon9.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.testcontainers.containers.PostgreSQLContainer;

/** Un contenedor por JVM, compartido entre todos los contextos de pruebas. */
public class TestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        String mode = environment.getProperty("test.database.mode", "container");
        if ("external".equals(mode)) {
            return;
        }
        if (!"container".equals(mode)) {
            throw new IllegalArgumentException("test.database.mode debe ser container o external");
        }
        environment.getPropertySources().addFirst(new MapPropertySource("testContainerDatabase", Holder.properties()));
    }

    // Inicializacion perezosa: el modo externo no consulta Docker.
    private static final class Holder {
        private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
        private static final String APP_PASSWORD = UUID.randomUUID().toString();
        private static final PostgreSQLContainer<?> DATABASE = startDatabase();

        private static PostgreSQLContainer<?> startDatabase() {
            var database = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("callejon9_test")
                    .withUsername("postgres")
                    .withPassword(UUID.randomUUID().toString());
            database.start();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(),
                    database.getUsername(), database.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("CREATE ROLE callejon9_owner LOGIN NOSUPERUSER NOBYPASSRLS "
                        + "NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD '" + OWNER_PASSWORD + "'");
                statement.execute("CREATE ROLE callejon9_app LOGIN NOSUPERUSER NOBYPASSRLS "
                        + "NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD '" + APP_PASSWORD + "'");
                statement.execute("ALTER DATABASE callejon9_test OWNER TO callejon9_owner");
                statement.execute("ALTER SCHEMA public OWNER TO callejon9_owner");
                statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                statement.execute("GRANT USAGE ON SCHEMA public TO callejon9_app");
                return database;
            } catch (SQLException failure) {
                database.stop();
                throw new IllegalStateException("No se pudieron preparar los roles de pruebas; SQLSTATE="
                        + failure.getSQLState());
            }
        }

        private static Map<String, Object> properties() {
            return Map.of(
                    "spring.datasource.url", DATABASE.getJdbcUrl(),
                    "spring.datasource.username", "callejon9_app",
                    "spring.datasource.password", APP_PASSWORD,
                    "spring.flyway.url", DATABASE.getJdbcUrl(),
                    "spring.flyway.user", "callejon9_owner",
                    "spring.flyway.password", OWNER_PASSWORD);
        }
    }
}
