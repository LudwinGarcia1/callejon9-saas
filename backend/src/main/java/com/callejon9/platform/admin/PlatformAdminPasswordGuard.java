package com.callejon9.platform.admin;

import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Impide que produccion opere con la contrasena publicada del super-administrador
 * (CAL-109).
 *
 * <p>{@code V6__seed_platform_admin.sql} siembra en cualquier base nueva la cuenta
 * {@code super@callejon9.com} del tenant tecnico {@code platform}, con una
 * contrasena que esta escrita en el repositorio. Sirve para desarrollo y para la
 * demo; en produccion equivale a no tener contrasena. {@code V6} ya esta aplicada
 * y las migraciones son append-only, asi que la correccion vive aqui: con el
 * perfil {@code prod}, al arrancar y despues de Flyway, se revisa esa cuenta.
 *
 * <ul>
 *   <li>Si su hash corresponde a la contrasena publicada y hay
 *       {@code PLATFORM_ADMIN_PASSWORD}, se reemplaza por esa contrasena.</li>
 *   <li>Si corresponde y no hay variable, el arranque se detiene.</li>
 *   <li>Si ya es otro hash, no se toca, con o sin variable: no deshace una
 *       rotacion posterior, como el cambio de contrasena de CAL-25.</li>
 *   <li>Si no existen el tenant o la cuenta, no hay nada que revisar.</li>
 * </ul>
 *
 * <p>Lee y escribe como {@code callejon9_app}, igual que el resto del sistema: el
 * tenant {@code platform} se publica con {@code set_config(..., true)} al abrir la
 * transaccion, a traves de {@link TenantContext}. No necesita {@code BYPASSRLS}.
 *
 * <p>Corre en {@link #afterSingletonsInstantiated()}: despues de Flyway y de crear
 * los repositorios, pero antes de que el servidor HTTP empiece a aceptar
 * conexiones. Si falla, el puerto nunca abre.
 *
 * <p>Ni la contrasena ni el hash aparecen en el log ni en los mensajes de error.
 */
@Component
@Profile("prod")
public class PlatformAdminPasswordGuard implements SmartInitializingSingleton {

    public static final String PLATFORM_SLUG = "platform";
    public static final String ADMIN_EMAIL = "super@callejon9.com";

    /** La contrasena que {@code V6} documenta para desarrollo y demo. Es publica. */
    public static final String PUBLISHED_PASSWORD = "Callejon9Demo!";

    public static final String VARIABLE = "PLATFORM_ADMIN_PASSWORD";
    public static final int MIN_LENGTH = 16;

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminPasswordGuard.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String configuredPassword;

    public PlatformAdminPasswordGuard(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            @Value("${app.platform-admin.password:}") String configuredPassword) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.configuredPassword = configuredPassword == null ? "" : configuredPassword.strip();
    }

    /**
     * Motivo por el que un valor de {@code PLATFORM_ADMIN_PASSWORD} no sirve, o
     * vacio si sirve. Tambien lo usa {@code ProductionConfigurationGuard} para
     * rechazarlo antes de tocar la base. El mensaje nunca incluye el valor.
     */
    public static Optional<String> problemWith(String value) {
        String candidate = value == null ? "" : value.strip();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        if (candidate.equals(PUBLISHED_PASSWORD)) {
            return Optional.of(VARIABLE + " usa la contrasena publicada del super-administrador");
        }
        if (candidate.length() < MIN_LENGTH) {
            return Optional.of(VARIABLE + " debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        return Optional.empty();
    }

    @Override
    public void afterSingletonsInstantiated() {
        problemWith(configuredPassword).ifPresent(problem -> {
            throw new IllegalStateException(problem);
        });

        // tenants no lleva RLS: es el catalogo de tenants y se lee sin contexto.
        Optional<UUID> platformTenant = transactionTemplate.execute(status ->
                tenantRepository.findBySlug(PLATFORM_SLUG).map(Tenant::getId));
        if (platformTenant == null || platformTenant.isEmpty()) {
            log.info("No existe el tenant '{}'; no hay super-administrador sembrado que revisar.", PLATFORM_SLUG);
            return;
        }

        TenantContext.callAs(platformTenant.get(), () -> transactionTemplate.execute(status -> {
            replacePublishedPassword();
            return null;
        }));
    }

    private void replacePublishedPassword() {
        Optional<User> admin = userRepository.findByEmail(ADMIN_EMAIL);
        if (admin.isEmpty()) {
            log.info("El tenant '{}' no tiene la cuenta sembrada {}; no hay nada que revisar.",
                    PLATFORM_SLUG, ADMIN_EMAIL);
            return;
        }

        User user = admin.get();
        if (!passwordEncoder.matches(PUBLISHED_PASSWORD, user.getPasswordHash())) {
            log.info("El super-administrador {} ya no usa la contrasena publicada; no se modifica.", ADMIN_EMAIL);
            return;
        }

        if (configuredPassword.isEmpty()) {
            throw new IllegalStateException("El super-administrador " + ADMIN_EMAIL
                    + " usa la contrasena publicada en el repositorio (V6__seed_platform_admin.sql). "
                    + "Define " + VARIABLE + " con " + MIN_LENGTH + " caracteres o mas para "
                    + "reemplazarla al arrancar; en Azure llega desde Key Vault (platform-admin-password).");
        }

        // Cambio por dirty checking: se escribe al confirmar la transaccion, con el
        // tenant platform ya publicado para la politica RLS de users.
        user.setPasswordHash(passwordEncoder.encode(configuredPassword));
        log.warn("Se reemplazo la contrasena publicada del super-administrador {} por la de {}.",
                ADMIN_EMAIL, VARIABLE);
    }
}
