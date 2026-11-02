package com.insurance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Powers the Swagger UI at /swagger-ui.html and the raw OpenAPI spec at /v3/api-docs.
 * Adds a "Bearer Token" security scheme so protected endpoints can be called directly
 * from the docs: click "Authorize", paste the JWT returned by /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI insuranceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Insurance Policy Management System API")
                        .description("REST API for managing customers, agents, policies, claims and payments. "
                                + "Log in via /api/auth/login to obtain a JWT, then click \"Authorize\" below "
                                + "and paste the token to call protected endpoints.")
                        .version("1.0.0")
                        .contact(new Contact().name("Insurance Backend Team")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
