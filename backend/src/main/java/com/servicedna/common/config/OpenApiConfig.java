package com.servicedna.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI serviceDnaOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ServiceDNA API")
                .version("v1")
                .description(
                    "Service health, dependency, incident, and observability platform. "
                        + "Most endpoints require a JWT — log in via POST /api/v1/auth/login "
                        + "or register via POST /api/v1/auth/register, then click Authorize "
                        + "below and paste the returned token."))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
