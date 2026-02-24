package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.application.service.CheapestFlightService;
import com.technoly.application.service.FlightAggregatorService;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FlightSearchController Unit Test (@WebMvcTest)
 *
 * @WebMvcTest tests only the MVC layer (Security, Services are excluded).
 * @MockBean mocks the service layer. Verifies HTTP routing, parameter parsing,
 *           JSON response structure, and validation.
 */
@WebMvcTest(FlightSearchController.class)
@TestPropertySource(properties = {
                "security.enabled=false",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
                "rate-limit.capacity=60",
                "rate-limit.refill-per-minute=60"
})
@DisplayName("FlightSearchController Unit Tests")
class FlightSearchControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private FlightAggregatorService flightAggregatorService;

        @MockBean
        private CheapestFlightService cheapestFlightService;

        @MockBean
        private ApiLogService apiLogService;

        private static final String BASE_DATE = "01-06-2026T15:00";

        private FlightDto sampleFlight() {
                return FlightDto.builder()
                                .flightNumber("TK1001")
                                .origin("IST")
                                .destination("COV")
                                .departureDateTime(LocalDateTime.of(2026, 6, 1, 15, 0))
                                .arrivalDateTime(LocalDateTime.of(2026, 6, 1, 18, 0))
                                .price(BigDecimal.valueOf(250))
                                .provider("PROVIDER_A")
                                .build();
        }

        // =============================================
        // /api/v1/flights/search
        // =============================================

        @Test
        @WithMockUser
        @DisplayName("search: returns 200 OK with mandatory params")
        void search_validParams_returns200() throws Exception {
                when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of(sampleFlight()));

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights").isArray())
                                .andExpect(jsonPath("$.flights.length()").value(1))
                                .andExpect(jsonPath("$.totalCount").value(1))
                                .andExpect(jsonPath("$.flights[0].flightNumber").value("TK1001"))
                                .andExpect(jsonPath("$.flights[0].price").value(250));
        }

        @Test
        @WithMockUser
        @DisplayName("search: returns 200 OK with priceMin/priceMax filters")
        void search_withPriceFilters_returns200() throws Exception {
                when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of(sampleFlight()));

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE)
                                .param("priceMin", "100")
                                .param("priceMax", "500"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights").isArray());
        }

        @Test
        @WithMockUser
        @DisplayName("search: returns 200 OK with date range filters")
        void search_withDateFilters_returns200() throws Exception {
                when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of(sampleFlight()));

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE)
                                .param("departureDateFrom", "01-06-2026T10:00")
                                .param("departureDateTo", "01-06-2026T18:00")
                                .param("arrivalDateFrom", "01-06-2026T14:00")
                                .param("arrivalDateTo", "01-06-2026T20:00"))
                                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        @DisplayName("search: missing origin -> 400 Bad Request")
        void search_missingOrigin_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("search: missing destination -> 400 Bad Request")
        void search_missingDestination_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("search: missing departureDate -> 400 Bad Request")
        void search_missingDepartureDate_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("search: invalid date format -> 400 Bad Request")
        void search_invalidDateFormat_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", "not-a-date"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("search: empty results -> 200 OK, empty list")
        void search_noResults_returns200WithEmptyList() throws Exception {
                when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of());

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights").isArray())
                                .andExpect(jsonPath("$.totalCount").value(0));
        }

        // =============================================
        // /api/v1/flights/search/cheapest
        // =============================================

        @Test
        @WithMockUser
        @DisplayName("cheapest: returns 200 OK with mandatory params")
        void cheapest_validParams_returns200() throws Exception {
                when(cheapestFlightService.findCheapestFlights(any())).thenReturn(List.of(sampleFlight()));

                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.flights.length()").value(1))
                                .andExpect(jsonPath("$.flights[0].provider").value("PROVIDER_A"));
        }

        @Test
        @WithMockUser
        @DisplayName("cheapest: returns 200 OK with all filters")
        void cheapest_withAllFilters_returns200() throws Exception {
                when(cheapestFlightService.findCheapestFlights(any())).thenReturn(List.of(sampleFlight()));

                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE)
                                .param("priceMin", "200")
                                .param("priceMax", "600")
                                .param("departureDateFrom", "01-06-2026T10:00"))
                                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        @DisplayName("cheapest: missing origin -> 400 Bad Request")
        void cheapest_missingOrigin_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/flights/search/cheapest")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("ApiLogService async logging bounds checks")
        void search_callsApiLogServiceOnce() throws Exception {
                when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of());

                mockMvc.perform(get("/api/v1/flights/search")
                                .param("origin", "IST")
                                .param("destination", "COV")
                                .param("departureDate", BASE_DATE))
                                .andExpect(status().isOk());

                verify(apiLogService, times(1)).logApiCall(
                                eq("/api/v1/flights/search"), any(), any(), eq(200), any());
        }
}
