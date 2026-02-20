package com.technoly.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 (Swagger) Konfigürasyonu
 *
 * SpringDoc OpenAPI, controller method'larındaki annotation'ları okuyarak
 * otomatik API dökümantasyonu oluşturur.
 * Bu bean, API genel bilgilerini (başlık, versiyon, iletişim) configure eder.
 *
 * Swagger UI erişim adresi: http://localhost:8080/swagger-ui.html
 * OpenAPI JSON: http://localhost:8080/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * OpenAPI bean'i: Swagger UI'ın başlık sekmeyi ve genel bilgileri
     * bu bean'den alır.
     */
    @Bean
    public OpenAPI flightAggregatorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flight Aggregator API")
                        .description("""
                                Flight Aggregator, FlightProvider A ve FlightProvider B sağlayıcılarından
                                uçuş bilgilerini paralel olarak toplar ve iki farklı REST servisi sunar:

                                - **Service 1** (`/api/v1/flights/search`): Her iki sağlayıcıdan tüm uçuşlar
                                - **Service 2** (`/api/v1/flights/search/cheapest`): Gruplanmış en ucuz uçuşlar

                                Her istek ve yanıt PostgreSQL'e asenkron olarak loglanır.
                                Redis ile cache, Resilience4j ile Circuit Breaker koruması mevcuttur.
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
                                .description("Geliştirme Sunucusu")));
    }
}
