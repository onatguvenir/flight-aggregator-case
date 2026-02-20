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
 * FlightSearchController Unit Testi (@WebMvcTest)
 *
 * @WebMvcTest: Sadece MVC katmanını test eder (Spring Security, servisler vb.
 *              dışlanır).
 *              Tam Spring context yerine sadece controller + MockMvc yükler.
 *              → Çok daha hızlı, izole controller testi.
 *
 * @MockBean: Servis katmanı mock'lanır. Test sadece HTTP katmanını doğrular:
 *            - URL mapping doğru mu?
 *            - Parametre parse ediliyor mu?
 *            - Response JSON yapısı doğru mu?
 *            - Validation çalışıyor mu?
 */
@WebMvcTest(FlightSearchController.class)
@DisplayName("FlightSearchController Unit Testleri")
class FlightSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlightAggregatorService flightAggregatorService;

    @MockBean
    private CheapestFlightService cheapestFlightService;

    @MockBean
    private ApiLogService apiLogService;

    private static final String BASE_DATE = "2026-06-01T00:00:00";

    private FlightDto sampleFlight() {
        return FlightDto.builder()
                .flightNumber("TK1001")
                .origin("IST")
                .destination("COV")
                .departureDateTime(LocalDateTime.of(2026, 6, 1, 9, 0))
                .arrivalDateTime(LocalDateTime.of(2026, 6, 1, 12, 0))
                .price(BigDecimal.valueOf(250))
                .provider("PROVIDER_A")
                .build();
    }

    // =============================================
    // /api/v1/flights/search
    // =============================================

    @Test
    @DisplayName("search: zorunlu parametrelerle 200 OK döner")
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
    @DisplayName("search: priceMin/priceMax filtreleri ile 200 OK döner")
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
    @DisplayName("search: tarih aralığı filtreleri ile 200 OK döner")
    void search_withDateFilters_returns200() throws Exception {
        when(flightAggregatorService.searchAllFlights(any())).thenReturn(List.of(sampleFlight()));

        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", "IST")
                .param("destination", "COV")
                .param("departureDate", BASE_DATE)
                .param("departureDateFrom", "2026-06-01T06:00:00")
                .param("departureDateTo", "2026-06-01T18:00:00")
                .param("arrivalDateFrom", "2026-06-01T09:00:00")
                .param("arrivalDateTo", "2026-06-01T23:00:00"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("search: origin eksik → 400 Bad Request")
    void search_missingOrigin_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flights/search")
                .param("destination", "COV")
                .param("departureDate", BASE_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search: destination eksik → 400 Bad Request")
    void search_missingDestination_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", "IST")
                .param("departureDate", BASE_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search: departureDate eksik → 400 Bad Request")
    void search_missingDepartureDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", "IST")
                .param("destination", "COV"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search: geçersiz tarih formatı → 400 Bad Request")
    void search_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flights/search")
                .param("origin", "IST")
                .param("destination", "COV")
                .param("departureDate", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("search: sonuç boş → 200 OK, boş liste")
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
    @DisplayName("cheapest: zorunlu parametrelerle 200 OK döner")
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
    @DisplayName("cheapest: tüm filtreler ile 200 OK döner")
    void cheapest_withAllFilters_returns200() throws Exception {
        when(cheapestFlightService.findCheapestFlights(any())).thenReturn(List.of(sampleFlight()));

        mockMvc.perform(get("/api/v1/flights/search/cheapest")
                .param("origin", "IST")
                .param("destination", "COV")
                .param("departureDate", BASE_DATE)
                .param("priceMin", "200")
                .param("priceMax", "600")
                .param("departureDateFrom", "2026-06-01T06:00:00"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cheapest: origin eksik → 400 Bad Request")
    void cheapest_missingOrigin_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/flights/search/cheapest")
                .param("destination", "COV")
                .param("departureDate", BASE_DATE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ApiLogService asenkron log çağrısı her istekte yapılır")
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
