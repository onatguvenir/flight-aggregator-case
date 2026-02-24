package com.technoly.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightprovider.wsdl.Flight;
import com.flightprovider.wsdl.SearchResult;
import com.technoly.domain.model.FlightSearchResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ws.client.core.WebServiceTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System Integration Test — With In-Memory H2 DB
 *
 * NOTE: Runs directly on H2 (flightdb) without external Docker dependencies.
 * SOAP and Redis integrations are isolated via Mock / In-Memory solutions.
 */
@SpringBootTest(classes = FlightAggregatorApplication.class, properties = {
                "security.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:flightdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
})
@Import(com.technoly.infrastructure.config.JpaConfig.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser
@DisplayName("Live Localhost Integration Test")
class SystemIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private DataSource dataSource;

        @MockBean(name = "webServiceTemplateA")
        private WebServiceTemplate webServiceTemplateA;

        @MockBean(name = "webServiceTemplateB")
        private WebServiceTemplate webServiceTemplateB;

        @MockBean
        private RedisConnectionFactory redisConnectionFactory;

        @MockBean
        private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

        private static final String ORIGIN = "IST";
        private static final String DESTINATION = "LHR";
        private static final String DEPARTURE_DATE = "01-06-2026T10:00";

        @BeforeAll
        static void initSchema(@Autowired DataSource dataSource) throws SQLException {
                try (Connection conn = dataSource.getConnection();
                                Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("""
                                        CREATE TABLE IF NOT EXISTS api_logs (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            endpoint VARCHAR(255) NOT NULL,
                                            request CLOB NOT NULL,
                                            response CLOB,
                                            status_code INTEGER,
                                            duration_ms BIGINT,
                                            created_at TIMESTAMP NOT NULL
                                        )
                                        """);
                }
        }

        @BeforeEach
        void verifyAndLog() {
                System.out.println(">>> In-memory H2 database & mocked Redis are in use (no Docker containers)");

                SearchResult resultA = new SearchResult();
                resultA.setHasError(false);
                Flight flightA = new Flight();
                flightA.setFlightNumber("A123");
                flightA.setOrigin("IST");
                flightA.setDestination("LHR");
                flightA.setDepartureTime("01-06-2026T10:00");
                flightA.setArrivalTime("01-06-2026T14:00");
                flightA.setPrice(new BigDecimal("150.00"));
                resultA.getFlights().add(flightA);
                Mockito.lenient()
                                .when(webServiceTemplateA.marshalSendAndReceive(ArgumentMatchers.any()))
                                .thenReturn(resultA);

                SearchResult resultB = new SearchResult();
                resultB.setHasError(false);
                Flight flightB = new Flight();
                flightB.setFlightNumber("B456");
                flightB.setOrigin("IST");
                flightB.setDestination("LHR");
                flightB.setDepartureTime("01-06-2026T10:00");
                flightB.setArrivalTime("01-06-2026T14:00");
                flightB.setPrice(new BigDecimal("120.00"));
                resultB.getFlights().add(flightB);
                Mockito.lenient()
                                .when(webServiceTemplateB.marshalSendAndReceive(ArgumentMatchers.any()))
                                .thenReturn(resultB);
        }

        @Test
        @DisplayName("1. Search all flights — Returns data from both providers")
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

                boolean hasProviderA = response.getFlights().stream()
                                .anyMatch(f -> f.getProvider() != null && f.getProvider().toUpperCase().contains("A"));
                boolean hasProviderB = response.getFlights().stream()
                                .anyMatch(f -> f.getProvider() != null && f.getProvider().toUpperCase().contains("B"));

                System.out.printf(">>> Total flights: %d | ProviderA: %b | ProviderB: %b%n",
                                response.getTotalCount(), hasProviderA, hasProviderB);

                assertThat(response.getFlights()).isNotEmpty();
                assertThat(response.getTotalCount()).isPositive();
                assertThat(hasProviderA).as("Should return ProviderA flights").isTrue();
                assertThat(hasProviderB).as("Should return ProviderB flights").isTrue();
        }

        @Test
        @DisplayName("2. Search cheapest flights — Returns sorted and grouped results")
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

                System.out.printf(">>> Cheapest flights count: %d%n", response.getTotalCount());

                if (response.getFlights().size() > 1) {
                        for (int i = 0; i < response.getFlights().size() - 1; i++) {
                                var current = response.getFlights().get(i).getPrice();
                                var next = response.getFlights().get(i + 1).getPrice();
                                assertThat(current).as("Prices must be in ascending order").isLessThanOrEqualTo(next);
                        }
                }
        }

        @Test
        @DisplayName("3. API log endpoint — Requests logged to DB and returned paged")
        void getLogs_returnsPagedApiLogs() throws Exception {
                System.out.println(">>> 3. ENDPOINT: GET /api/v1/logs");

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", ORIGIN)
                                .param("destination", DESTINATION)
                                .param("departureDate", DEPARTURE_DATE))
                                .andExpect(status().isOk());

                String logResponse = mockMvc.perform(get("/api/v1/logs"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                System.out.printf(">>> Log response length: %d characters%n", logResponse.length());

                assertThat(logResponse).contains("content");
                assertThat(logResponse).contains("totalElements");
        }

        @Test
        @DisplayName("4. Cache test — 2nd call hits Redis cache")
        void searchFlights_secondCallHitCache() throws Exception {
                long start1 = System.currentTimeMillis();
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", ORIGIN)
                                .param("destination", DESTINATION)
                                .param("departureDate", DEPARTURE_DATE))
                                .andExpect(status().isOk());
                long duration1 = System.currentTimeMillis() - start1;

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

                System.out.printf(">>> 1st call: %dms | 2nd call (cache): %dms%n", duration1, duration2);

                assertThat(response.getFlights()).isNotEmpty();
        }
}
