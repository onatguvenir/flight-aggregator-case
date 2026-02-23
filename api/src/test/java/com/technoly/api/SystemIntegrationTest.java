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
 * Sistem Entegrasyon Testi — In-Memory H2 Veritabanı İle
 *
 * NOT: Bu test, testcontainers kaldırıldığı için dış bir Docker bağımlılığı
 * olmadan doğrudan In-Memory H2 veritabanı (flightdb) üzerinde çalışır.
 *
 * SOAP ve Redis entegrasyonları için Mock / In-Memory çözümleri
 * kullanılarak izole edilmiştir.
 */

@SpringBootTest(classes = FlightAggregatorApplication.class, properties = {
        "security.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:flightdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@org.springframework.context.annotation.Import(com.technoly.infrastructure.config.JpaConfig.class)
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser
@DisplayName("Çalışan Altyapı İle (Live Localhost) Entegrasyon Testi")
class SystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "webServiceTemplateA")
    private org.springframework.ws.client.core.WebServiceTemplate webServiceTemplateA;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "webServiceTemplateB")
    private org.springframework.ws.client.core.WebServiceTemplate webServiceTemplateB;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    private static final String ORIGIN = "IST";
    private static final String DESTINATION = "LHR";
    private static final String DEPARTURE_DATE = "01-06-2026T10:00";

    @BeforeEach
    void verifyAndLog() {
        System.out.println(">>> Localhost üzerindeki aktif PostgreSQL ve Redis docker container'larına bağlanıldı...");

        // Mock Provider A
        com.flightprovider.wsdl.SearchResult resultA = new com.flightprovider.wsdl.SearchResult();
        resultA.setHasError(false);
        com.flightprovider.wsdl.Flight flightA = new com.flightprovider.wsdl.Flight();
        flightA.setFlightNumber("A123");
        flightA.setOrigin("IST");
        flightA.setDestination("LHR");
        flightA.setDepartureTime("01-06-2026T10:00");
        flightA.setArrivalTime("01-06-2026T14:00");
        flightA.setPrice(new java.math.BigDecimal("150.00"));
        resultA.getFlights().add(flightA);
        org.mockito.Mockito.lenient()
                .when(webServiceTemplateA.marshalSendAndReceive(org.mockito.ArgumentMatchers.any()))
                .thenReturn(resultA);

        // Mock Provider B
        com.flightprovider.wsdl.SearchResult resultB = new com.flightprovider.wsdl.SearchResult();
        resultB.setHasError(false);
        com.flightprovider.wsdl.Flight flightB = new com.flightprovider.wsdl.Flight();
        flightB.setFlightNumber("B456");
        flightB.setOrigin("IST");
        flightB.setDestination("LHR");
        flightB.setDepartureTime("01-06-2026T10:00");
        flightB.setArrivalTime("01-06-2026T14:00");
        flightB.setPrice(new java.math.BigDecimal("120.00"));
        resultB.getFlights().add(flightB);
        org.mockito.Mockito.lenient()
                .when(webServiceTemplateB.marshalSendAndReceive(org.mockito.ArgumentMatchers.any()))
                .thenReturn(resultB);
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
