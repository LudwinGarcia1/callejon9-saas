package com.callejon9.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Impide que el perfil {@code prod} arranque con configuracion de desarrollo.
 *
 * <p>{@code application.yml} trae valores por defecto para que el proyecto
 * corra en local sin preparar nada: un secreto JWT y contrasenas de base de
 * datos que estan publicadas en el repositorio. En produccion esos valores
 * equivalen a no tener secreto, asi que con {@code prod} activo la aplicacion
 * se niega a arrancar si falta alguno, si es corto o si es uno de esos valores.
 *
 * <p>Es un {@link BeanFactoryPostProcessor} para correr antes de crear
 * cualquier bean: el arranque se detiene antes de conectarse a la base, de
 * migrar con Flyway y de abrir el puerto HTTP.
 *
 * <p>Los mensajes nombran la variable de entorno y el motivo, nunca el valor:
 * el log de arranque suele terminar en sistemas con mas lectores que el
 * secreto.
 *
 * <p>Fuera de {@code prod} no bloquea nada, pero advierte en el log que la
 * configuracion no es apta para un entorno real.
 */
@Component
public class ProductionConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String PRODUCTION_PROFILE = "prod";

    /** Perfiles pensados para una maquina local; ninguno convive con prod. */
    static final Set<String> NON_PRODUCTION_PROFILES = Set.of("demo", "dev");

    /** HS256 exige una clave de al menos 256 bits. */
    static final int MIN_JWT_SECRET_BYTES = 32;

    /** El secreto entre Next y la API autentica la IP del cliente (CAL-169). */
    static final int MIN_PROXY_SECRET_BYTES = 32;

    /**
     * Secretos JWT que aparecen en el repositorio: el valor por defecto, el de
     * CI y el ejemplo de la documentacion. Cualquiera puede firmar tokens con
     * ellos. Se comparan sin espacios en los extremos: un {@code .env} con
     * fin de linea Windows o un espacio sobrante no los vuelve secretos.
     */
    static final Set<String> PUBLISHED_JWT_SECRETS = Set.of(
            "dev-only-secret-change-me-min-32-bytes-long!!",
            "ci-only-secret-at-least-32-bytes-long!!",
            "cambia-esto-por-un-secreto-de-al-menos-32-bytes");

    /** Contrasenas de los roles de base de datos en scripts/setup-db.sql. */
    static final Set<String> PUBLISHED_DB_PASSWORDS = Set.of("app_dev_pwd", "owner_dev_pwd");

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigurationGuard.class);

    // Por setter y no por constructor: un BeanFactoryPostProcessor se crea
    // antes de que exista la inyeccion de dependencias por constructor.
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (environment.acceptsProfiles(Profiles.of(PRODUCTION_PROFILE))) {
            List<String> problems = productionProblems();
            if (!problems.isEmpty()) {
                throw new IllegalStateException("El perfil prod no puede arrancar con esta configuracion:\n  - "
                        + String.join("\n  - ", problems));
            }
            return;
        }
        warnIfNotProductionReady();
    }

    private List<String> productionProblems() {
        List<String> problems = new ArrayList<>();

        List<String> localProfiles = activeLocalProfiles();
        if (!localProfiles.isEmpty()) {
            problems.add("los perfiles " + localProfiles + " son solo para desarrollo y no pueden activarse junto a prod");
        }

        String jwtSecret = environment.getProperty("app.jwt.secret", "");
        if (jwtSecret.isBlank()) {
            problems.add("JWT_SECRET no esta definida");
        } else if (PUBLISHED_JWT_SECRETS.contains(jwtSecret.strip())) {
            problems.add("JWT_SECRET usa un valor publicado en el repositorio");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET debe tener al menos " + MIN_JWT_SECRET_BYTES + " bytes");
        }

        // Opcional: sin el, los limites por IP usan X-Forwarded-For. Si se define,
        // es lo unico que impide falsificar la IP del cliente ante la API.
        String proxySecret = environment.getProperty("app.client-ip.proxy-secret", "").strip();
        if (!proxySecret.isEmpty()
                && proxySecret.getBytes(StandardCharsets.UTF_8).length < MIN_PROXY_SECRET_BYTES) {
            problems.add("APP_PROXY_SECRET debe tener al menos " + MIN_PROXY_SECRET_BYTES + " bytes");
        }

        checkDatabasePassword("DB_APP_PASSWORD", "spring.datasource.password", problems);
        checkDatabasePassword("DB_OWNER_PASSWORD", "spring.flyway.password", problems);

        if (!environment.getProperty("app.auth.cookie-secure", Boolean.class, false)) {
            problems.add("app.auth.cookie-secure debe ser true: las cookies de sesion solo viajan por HTTPS");
        }

        String origins = environment.getProperty("app.realtime.allowed-origins", "");
        if (origins.isBlank()) {
            problems.add("APP_ALLOWED_ORIGINS no esta definida");
        } else if (Arrays.stream(origins.split(",")).map(String::strip).anyMatch(o -> !o.startsWith("https://"))) {
            problems.add("APP_ALLOWED_ORIGINS solo admite origenes https://");
        }

        return problems;
    }

    private List<String> activeLocalProfiles() {
        return Arrays.stream(environment.getActiveProfiles())
                .filter(NON_PRODUCTION_PROFILES::contains)
                .toList();
    }

    private void checkDatabasePassword(String variable, String property, List<String> problems) {
        String password = environment.getProperty(property, "");
        if (password.isBlank()) {
            problems.add(variable + " no esta definida");
        } else if (PUBLISHED_DB_PASSWORDS.contains(password.strip())) {
            problems.add(variable + " usa la contrasena de desarrollo");
        }
    }

    private void warnIfNotProductionReady() {
        List<String> localProfiles = activeLocalProfiles();
        boolean publishedSecret = PUBLISHED_JWT_SECRETS.contains(environment.getProperty("app.jwt.secret", "").strip());
        if (localProfiles.isEmpty() && !publishedSecret) {
            return;
        }
        log.warn("Configuracion NO apta para produccion (perfiles {}, JWT_SECRET {}). "
                        + "Para un entorno real activa el perfil prod.",
                localProfiles.isEmpty() ? "por defecto" : localProfiles,
                publishedSecret ? "publicado en el repositorio" : "propio");
    }
}
