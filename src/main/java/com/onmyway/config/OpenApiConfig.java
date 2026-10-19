package com.onmyway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI onMyWayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OnMyWay API")
                        .version("v1")
                        .description("REST API for discovering cities and places, managing place submissions, and moderating published content.")
                        .contact(new Contact().name("OnMyWay")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token obtained from POST /api/auth/login. Enter the token without the 'Bearer ' prefix.")));
    }
}
