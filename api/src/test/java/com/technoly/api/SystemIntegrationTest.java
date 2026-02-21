package com.technoly.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.domain.model.FlightSearchResponse;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import com.technoly.infrastructure.persistence.repository.ApiLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = FlightAggregatorApplication.class, properties = {
        // DB, JPA, Flyway ve Redis'i Spring Boot contextinde devre dışı bırakıyoruz
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
})
@AutoConfigureMockMvc
@DisplayName("Simüle Edilmiş Altyapı İle Provider Gerçek Çağrı Testi")
class SystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Altyapı bağımlılıkları mocklanıyor
    @MockBean
    private ApiLogRepository apiLogRepository;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private org.springframework.cache.CacheManager cacheManager;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.cache.concurrent.ConcurrentMapCache cache1 = new org.springframework.cache.concurrent.ConcurrentMapCache(
                "flightSearch");
        org.springframework.cache.concurrent.ConcurrentMapCache cache2 = new org.springframework.cache.concurrent.ConcurrentMapCache(
                "cheapestFlights");
        org.mockito.Mockito.when(cacheManager.getCache("flightSearch")).thenReturn(cache1);
        org.mockito.Mockito.when(cacheManager.getCache("cheapestFlights")).thenReturn(cache2);
    }

    @Test
    @DisplayName("Her Üç Endpointin DB ve Redis Hariç Uçtan Uca Sınanması")
    void testAllEndpointsEndToEnd() throws Exception {
        // İSTEK 1: Tüm uçuşlar endpoint'ine istek at (Burda GERÇEK provider kütüphanesi
        // çağrılır)
        System.out.println(">>> 1. ENDPOINT: GET /api/v1/flights/search");
        MvcResult searchResult = mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", "IST")
                .param("destination", "LON")
                .param("departureDate", "2026-06-01T10:00:00"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        FlightSearchResponse response = objectMapper.readValue(
                searchResult.getResponse().getContentAsString(), FlightSearchResponse.class);

        // Assert ProviderA and ProviderB both returned data (Gerçek JAR kütüphanesi
        // çalıştığı için data doludur)
        boolean hasProviderA = response.getFlights().stream().anyMatch(f -> f.getProvider().contains("ProviderA")
                || f.getProvider().contains("Provider A") || f.getProvider().contains("FlightProviderA"));
        boolean hasProviderB = response.getFlights().stream().anyMatch(f -> f.getProvider().contains("ProviderB")
                || f.getProvider().contains("Provider B") || f.getProvider().contains("FlightProviderB"));

        assertThat(hasProviderA).isTrue();
        assertThat(hasProviderB).isTrue();

        System.out.println("Tüm Uçuşlar Toplam Sonuç: " + response.getTotalCount());

        // İSTEK 2: En ucuz gruplanmış uçuşlar endpoint'ine istek at
        System.out.println(">>> 2. ENDPOINT: GET /api/v1/flights/search/cheapest");
        MvcResult cheapestResult = mockMvc.perform(get("/api/v1/flights/search/cheapest")
                .param("origin", "IST")
                .param("destination", "LON")
                .param("departureDate", "2026-06-01T10:00:00"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        FlightSearchResponse cheapestResponse = objectMapper.readValue(
                cheapestResult.getResponse().getContentAsString(), FlightSearchResponse.class);

        System.out.println("En Ucuz Uçuşlar Toplam Sonuç: " + cheapestResponse.getTotalCount());

        // İSTEK 3: API Loglarının DB'den getirildiğini simüle eden endpoint (Mock
        // Repository var)
        System.out.println(">>> 3. ENDPOINT: GET /api/v1/logs");

        when(apiLogRepository.findAll()).thenReturn(List.of(
                ApiLogEntity.builder().endpoint("/api/v1/flights/search").statusCode(200).build(),
                ApiLogEntity.builder().endpoint("/api/v1/flights/search/cheapest").statusCode(200).build()));

        MvcResult logResult = mockMvc.perform(get("/api/v1/logs"))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String logContent = logResult.getResponse().getContentAsString();
        System.out.println("Gelen Log İçeriği Karakter Sayısı: " + logContent.length());

        ApiLogEntity[] logs = objectMapper.readValue(logContent, ApiLogEntity[].class);
        assertThat(logs).hasSize(2);

        System.out.println(">>> BÜTÜN ENDPOINTLER VERİ DÖNDÜREREK BAŞARIYLA TAMAMLANDI <<<");
    }
}
