package com.callejon9.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    @Bean
    public OpenAPI callejon9OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Callejon 9 SaaS API")
                        .version("v1")
                        .description("API multi-tenant para gestion de restaurantes. "
                                + "El aislamiento entre restaurantes lo impone PostgreSQL "
                                + "mediante Row Level Security."))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME));
    }
}
