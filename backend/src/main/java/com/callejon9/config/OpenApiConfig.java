package com.callejon9.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI callejon9OpenApi() {
        return new OpenAPI().info(new Info()
                .title("Callejon 9 SaaS API")
                .version("v1")
                .description("API multi-tenant para gestion de restaurantes. "
                        + "El aislamiento entre restaurantes lo impone PostgreSQL "
                        + "mediante Row Level Security."));
    }
}
