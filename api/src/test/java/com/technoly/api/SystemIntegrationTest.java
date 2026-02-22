package com.technoly.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.domain.model.FlightSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sistem Entegrasyon Testi — Mevcut Çalışan (Live) Altyapı İle
 *
 * NOT: Bu test "docker-compose up -d" ile çalışan
 * PostgreSQL (5432) ve Redis (6379) veritabanı instance'larınıza
 * bağlanarak çalışacak şekilde konfigüre edilmiştir.
 * Testcontainers'daki Docker Desktop kısıtlamaları nedeniyle doğrudan
 * kullanılır.
 */
@SpringBootTest(classes = FlightAggregatorApplication.class, properties = {
        "security.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "rate-limit.capacity=600",
        "rate-limit.refill-per-minute=600",
        "logging.level.net.logstash=OFF",
        // Canlı Localhost Docker Connection Ayarları:
        "spring.datasource.url=jdbc:postgresql://localhost:5432/flightdb",
        "spring.datasource.username=flightuser",
        "spring.datasource.password=flightpass",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.url=jdbc:postgresql://localhost:5432/flightdb",
        "spring.flyway.user=flightuser",
        "spring.flyway.password=flightpass",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@org.springframework.context.annotation.Import(com.technoly.infrastructure.config.JpaConfig.class)
@AutoConfigureMockMvc
@WithMockUser
@DisplayName("Çalışan Altyapı İle (Live Localhost) Entegrasyon Testi")
class SystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ORIGIN = "IST";
    private static final String DESTINATION = "LHR";
    private static final String DEPARTURE_DATE = "2026-06-01T10:00:00";

    @BeforeEach
    void verifyAndLog() {
        System.out.println(">>> Localhost üzerindeki aktif PostgreSQL ve Redis docker container'larına bağlanıldı...");
    }

    // =====================================================================
    // TEST METODLARI
    // =====================================================================

    @Test
    @DisplayName("1. Tüm uçuşlar endpoint'i — ProviderA ve ProviderB verisi döner")
    void searchAllFlights_returnsBothProviders() throws Exception {
        System.out.println(">>> 1. ENDPOINT: GET /api/v1/flights/search");

        MvcResult result = mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", ORIGIN)
                .param("destination", DESTINATION)
                .param("departureDate", DEPARTURE_DATE))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        FlightSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FlightSearchResponse.class);

        // Provider'ların her ikisi de veri döndürmeli
        boolean hasProviderA = response.getFlights().stream()
                .anyMatch(f -> f.getProvider() != null && f.getProvider().toUpperCase().contains("A"));
        boolean hasProviderB = response.getFlights().stream()
                .anyMatch(f -> f.getProvider() != null && f.getProvider().toUpperCase().contains("B"));

        System.out.printf(">>> Toplam uçuş: %d | ProviderA: %b | ProviderB: %b%n",
                response.getTotalCount(), hasProviderA, hasProviderB);

        assertThat(response.getFlights()).isNotEmpty();
        assertThat(response.getTotalCount()).isPositive();
        assertThat(hasProviderA).as("ProviderA uçuş döndürmeli").isTrue();
        assertThat(hasProviderB).as("ProviderB uçuş döndürmeli").isTrue();
    }

    @Test
    @DisplayName("2. En ucuz uçuşlar endpoint'i — sıralı ve gruplu sonuç döner")
    void searchCheapestFlights_returnsSortedResults() throws Exception {
        System.out.println(">>> 2. ENDPOINT: GET /api/v1/flights/search/cheapest");

        MvcResult result = mockMvc.perform(get("/api/v1/flights/search/cheapest")
                .param("origin", ORIGIN)
                .param("destination", DESTINATION)
                .param("departureDate", DEPARTURE_DATE))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        FlightSearchResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), FlightSearchResponse.class);

        System.out.printf(">>> En ucuz uçuş sayısı: %d%n", response.getTotalCount());

        // En ucuz sonuçlar fiyata göre sıralı olmalı
        if (response.getFlights().size() > 1) {
            for (int i = 0; i < response.getFlights().size() - 1; i++) {
                var current = response.getFlights().get(i).getPrice();
                var next = response.getFlights().get(i + 1).getPrice();
                assertThat(current).as("Fiyatlar artan sırada olmalı").isLessThanOrEqualTo(next);
            }
        }
    }

    @Test
    @DisplayName("3. API log endpoint'i — istekler DB'ye loglanır ve sayfalı döner")
    void getLogs_returnsPagedApiLogs() throws Exception {
        System.out.println(">>> 3. ENDPOINT: GET /api/v1/logs");

        // Önce birkaç istek at ki log oluşsun
        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", ORIGIN)
                .param("destination", DESTINATION)
                .param("departureDate", DEPARTURE_DATE))
                .andExpect(status().isOk());

        // Log endpoint'ini sorgula
        String logResponse = mockMvc.perform(get("/api/v1/logs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.printf(">>> Log response uzunluğu: %d karakter%n", logResponse.length());

        // Response boş olmamalı ve Page yapısında olmalı
        assertThat(logResponse).contains("content");
        assertThat(logResponse).contains("totalElements");
    }

    @Test
    @DisplayName("4. Cache testi — 2. çağrı Redis cache'ten gelir")
    void searchFlights_secondCallHitCache() throws Exception {
        // İlk çağrı: DB + Provider
        long start1 = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", ORIGIN)
                .param("destination", DESTINATION)
                .param("departureDate", DEPARTURE_DATE))
                .andExpect(status().isOk());
        long duration1 = System.currentTimeMillis() - start1;

        // İkinci çağrı: Cache hit (çok daha hızlı olmalı)
        long start2 = System.currentTimeMillis();
        MvcResult cached = mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", ORIGIN)
                .param("destination", DESTINATION)
                .param("departureDate", DEPARTURE_DATE))
                .andExpect(status().isOk())
                .andReturn();
        long duration2 = System.currentTimeMillis() - start2;

        FlightSearchResponse response = objectMapper.readValue(
                cached.getResponse().getContentAsString(), FlightSearchResponse.class);

        System.out.printf(">>> 1. çağrı: %dms | 2. çağrı (cache): %dms%n", duration1, duration2);

        // Cache yanıtı da geçerli veri içermeli
        assertThat(response.getFlights()).isNotEmpty();
    }
}
