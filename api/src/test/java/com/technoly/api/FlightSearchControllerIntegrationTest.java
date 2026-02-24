package com.technoly.api;

import com.technoly.application.service.ApiLogService;
import com.technoly.application.service.CheapestFlightService;
import com.technoly.application.service.FlightAggregatorService;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FlightSearchController Integration Test
 *
 * Docker-free integration test:
 * - Uses H2 In-Memory DB
 * - External Redis connection is bypassed via MockBean
 *
 * SOAP providers (FlightAggregatorService, CheapestFlightService) are mocked
 * to test only the REST layer.
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
                "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("FlightSearchController Integration Tests")
class FlightSearchControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private FlightAggregatorService flightAggregatorService;

        @MockBean
        private CheapestFlightService cheapestFlightService;

        @MockBean
        private ApiLogService apiLogService;

        @MockBean
        private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

        @MockBean
        private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

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

        @Test
        @DisplayName("GET /api/v1/flights/search - Returns 200 with valid parameters")
        void searchAllFlights_validParams_returns200() throws Exception {
                when(flightAggregatorService.searchAllFlights(any(FlightSearchRequest.class)))
                                .thenReturn(testFlights);

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "01-06-2026T15:00"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(content().contentType("application/json"))
                                .andExpect(jsonPath("$.flights").isArray())
                                .andExpect(jsonPath("$.flights.length()").value(2))
                                .andExpect(jsonPath("$.totalCount").value(2))
                                .andExpect(jsonPath("$.searchedAt").exists());
        }

        @Test
        @DisplayName("GET /api/v1/flights/search - Returns 400 when origin is missing")
        void searchAllFlights_missingOrigin_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("destination", "COV")
                                .param("departureDate", "01-06-2026T15:00"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/flights/search - Returns 400 for invalid date format")
        void searchAllFlights_invalidDateFormat_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "invalid-date"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/flights/search/cheapest - Returns 200 with valid parameters")
        void searchCheapestFlights_validParams_returns200() throws Exception {
                when(cheapestFlightService.findCheapestFlights(any(FlightSearchRequest.class)))
                                .thenReturn(List.of(testFlights.get(0)));

                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "01-06-2026T15:00"))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights.length()").value(1))
                                .andExpect(jsonPath("$.flights[0].price").value(250))
                                .andExpect(jsonPath("$.flights[0].provider").value("PROVIDER_A"));
        }

        @Test
        @DisplayName("GET /api/v1/flights/search/cheapest - Returns 400 when destination is missing")
        void searchCheapestFlights_missingDestination_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("departureDate", "01-06-2026T15:00"))
                                .andDo(print())
                                .andExpect(status().isBadRequest());
        }
}
