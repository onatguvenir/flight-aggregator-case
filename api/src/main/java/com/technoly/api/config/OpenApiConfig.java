package com.technoly.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 (Swagger) Configuration
 */
@Configuration
public class OpenApiConfig {

        private static final String BEARER_AUTH = "BearerAuth";

        @Value("${server.port:8080}")
        private String serverPort;

        @Bean
        public OpenAPI flightAggregatorOpenApi() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Flight Aggregator API")
                                                .description("""
                                                                Flight Aggregator collects flight data from FlightProvider A and FlightProvider B
                                                                in parallel and provides two REST services:

                                                                - **Service 1** (`/api/v1/flights/search`): All flights from both providers
                                                                - **Service 2** (`/api/v1/flights/search/cheapest`): Cheapest flights per group

                                                                Requests and responses are asynchronously logged to PostgreSQL.
                                                                Includes Redis caching and Resilience4j Circuit Breaker protection.
                                                                """)
                                                .version("1.0.0")
                                                .contact(new Contact()
                                                                .name("Technoly Case")
                                                                .email("dev@technoly.com"))
                                                .license(new License()
                                                                .name("MIT")
                                                                .url("https://opensource.org/licenses/MIT")))
                                .servers(List.of(
                                                new Server()
                                                                .url("http://localhost:" + serverPort)
                                                                .description("Development Server")))
                                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                                .components(new Components()
                                                .addSecuritySchemes(BEARER_AUTH,
                                                                new SecurityScheme()
                                                                                .name(BEARER_AUTH)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")));
        }
}
