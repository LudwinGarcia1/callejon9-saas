package com.callejon9.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * La API se autentica con la cookie httpOnly {@code access_token}
     * (ver {@link com.callejon9.tenancy.TenantFilter}), no con un header
     * Bearer. Sin declarar este esquema, Swagger UI no tiene forma de
     * autenticar sus peticiones y media API queda imposible de probar desde
     * ahi.
     */
    private static final String COOKIE_AUTH_SCHEME = "cookieAuth";

    /**
     * Servidor relativo al origen de la pagina. Sin el, springdoc calcula la
     * URL a partir de la peticion; en App Service el TLS termina en el proxy y
     * {@code forward-headers-strategy: none} la deja en {@code http://}, asi
     * que el navegador bloquearia cada "Try it out" de Swagger UI por
     * contenido mixto.
     */
    private static final Server SAME_ORIGIN = new Server()
            .url("/")
            .description("Mismo origen que la pagina: Swagger UI del backend o el servidor del frontend");

    @Bean
    public OpenAPI callejon9OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Callejon 9 SaaS API")
                        .version("v1")
                        .description("API multi-tenant para gestion de restaurantes. "
                                + "El aislamiento entre restaurantes lo impone PostgreSQL "
                                + "mediante Row Level Security."))
                .servers(List.of(SAME_ORIGIN))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME));
    }
}
