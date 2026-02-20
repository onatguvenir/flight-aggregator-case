package com.technoly.api;

import com.technoly.application.service.ApiLogService;
import com.technoly.application.service.CheapestFlightService;
import com.technoly.application.service.FlightAggregatorService;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.model.FlightSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FlightSearchController Integration Testi
 *
 * Testcontainers ile gerçek altyapı bileşenleri kullanılır:
 * - PostgreSQL 16: Gerçek DB (H2 değil!) Flyway migration otomatik çalışır
 * - Redis 7: Gerçek cache (EmbeddedRedis değil!)
 *
 * SOAP provider'lar (FlightAggregatorService, CheapestFlightService) mock'lanır
 * çünkü burada sadece REST katmanını test ediyoruz.
 *
 * @Testcontainers: JUnit 5 extension, container lifecycle'ı yönetir.
 *                  disabledWithoutDocker=true: Docker yoksa (CI/CD, bazı
 *                  gelistirici
 *                  makineleri) test SKIP edilir, FAIL olmaz. Bu sayede
 *                  Docker'sız
 *                  ortamlarda build kırılmaz.
 * @Container + static: Tüm testler için tek container (sınıf seviyesinde)
 *
 * @SpringBootTest: Tam Spring context yükler (integration test için)
 * @AutoConfigureMockMvc: MockMvc otomatik configure edilir
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = FlightAggregatorApplication.class)
@AutoConfigureMockMvc
@DisplayName("FlightSearchController Integration Testleri")
class FlightSearchControllerIntegrationTest {

        // ---- Testcontainers: Gerçek Altyapı ----

        /**
         * PostgreSQL container: Gerçek PostgreSQL 16 instance başlatır.
         * static = Tüm test metodları için paylaşılır (performans optimizasyonu)
         */
        @Container
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                        DockerImageName.parse("postgres:16-alpine"))
                        .withDatabaseName("flightdb")
                        .withUsername("flightuser")
                        .withPassword("flightpass");

        /**
         * Redis container: Gerçek Redis 7 instance.
         */
        @Container
        static GenericContainer<?> redis = new GenericContainer<>(
                        DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);

        /**
         * DynamicPropertySource: Testcontainers'ın dinamik portlarını
         * Spring context'e aktarır.
         * Container başlamadan önce port bilinmediğinden @Value kullanılamaz;
         * bu yüzden dynamic property registration gerekir.
         */
        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }

        // ---- Spring Beans ----

        @Autowired
        private MockMvc mockMvc;

        /**
         * SOAP servisleri mock'lanır: Integration testinde gerçek SOAP provider yoktur.
         * Testcontainers sadece DB ve Redis'i başlatır.
         * 
         * @MockBean: Spring context'e mock bean enjekte eder.
         */
        @MockBean
        private FlightAggregatorService flightAggregatorService;

        @MockBean
        private CheapestFlightService cheapestFlightService;

        @MockBean
        private ApiLogService apiLogService;

        private List<FlightDto> testFlights;

        @BeforeEach
        void setUp() {
                LocalDateTime dep = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0)
                                .withNano(0);
                LocalDateTime arr = dep.plusHours(3);

                testFlights = List.of(
                                FlightDto.builder()
                                                .flightNumber("TK1001")
                                                .origin("IST")
                                                .destination("COV")
                                                .departureDateTime(dep)
                                                .arrivalDateTime(arr)
                                                .price(BigDecimal.valueOf(250))
                                                .provider("PROVIDER_A")
                                                .build(),
                                FlightDto.builder()
                                                .flightNumber("TK1001")
                                                .origin("IST")
                                                .destination("COV")
                                                .departureDateTime(dep)
                                                .arrivalDateTime(arr)
                                                .price(BigDecimal.valueOf(300))
                                                .provider("PROVIDER_B")
                                                .build());
        }

        // ---- Tests: /api/v1/flights/search ----

        @Test
        @DisplayName("GET /api/v1/flights/search - Geçerli parametrelerle 200 döner")
        void searchAllFlights_validParams_returns200() throws Exception {
                // GIVEN: Service mock davranışı
                when(flightAggregatorService.searchAllFlights(any(FlightSearchRequest.class)))
                                .thenReturn(testFlights);

                // WHEN & THEN: MockMvc ile HTTP isteği gönder ve yanıtı doğrula
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "2026-06-01T00:00:00"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(content().contentType("application/json"))
                                .andExpect(jsonPath("$.flights").isArray())
                                .andExpect(jsonPath("$.flights.length()").value(2))
                                .andExpect(jsonPath("$.totalCount").value(2))
                                .andExpect(jsonPath("$.searchedAt").exists());
        }

        @Test
        @DisplayName("GET /api/v1/flights/search - origin eksik olunca 400 döner")
        void searchAllFlights_missingOrigin_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("destination", "COV")
                                .param("departureDate", "2026-06-01T00:00:00"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/flights/search - Geçersiz tarih formatında 400 döner")
        void searchAllFlights_invalidDateFormat_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "invalid-date"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        // ---- Tests: /api/v1/flights/search/cheapest ----

        @Test
        @DisplayName("GET /api/v1/flights/search/cheapest - Geçerli parametrelerle 200 döner")
        void searchCheapestFlights_validParams_returns200() throws Exception {
                // Cheapest: sadece en ucuz (250) döner
                when(cheapestFlightService.findCheapestFlights(any(FlightSearchRequest.class)))
                                .thenReturn(List.of(testFlights.get(0)));

                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "2026-06-01T00:00:00"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights.length()").value(1))
                                .andExpect(jsonPath("$.flights[0].price").value(250))
                                .andExpect(jsonPath("$.flights[0].provider").value("PROVIDER_A"));
        }

        @Test
        @DisplayName("GET /api/v1/flights/search/cheapest - destination eksik olunca 400 döner")
        void searchCheapestFlights_missingDestination_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("departureDate", "2026-06-01T00:00:00"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }
}
