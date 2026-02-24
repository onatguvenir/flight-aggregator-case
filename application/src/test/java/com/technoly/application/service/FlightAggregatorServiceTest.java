package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * FlightAggregatorService Unit Test
 *
 * Test strategy:
 * - Calling raw flights via FlightSearchPort (Adapter).
 * - Verifying the logic of applying FlightFilterService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlightAggregatorService Unit Tests")
class FlightAggregatorServiceTest {

    @Mock
    private FlightSearchPort flightSearchPort;

    private FlightFilterService filterService;

    private FlightAggregatorService aggregatorService;

    private FlightSearchRequest testRequest;

    @BeforeEach
    void setUp() {
        filterService = mock(FlightFilterService.class);
        aggregatorService = new FlightAggregatorService(flightSearchPort, filterService);

        testRequest = FlightSearchRequest.builder()
                .origin("IST")
                .destination("COV")
                .departureDate(LocalDateTime.now().plusDays(30))
                .build();
    }

    @Test
    @DisplayName("FlightSearchPort is called and filters are applied")
    void shouldCallPortAndApplyFilters() {
        // GIVEN
        List<FlightDto> rawFlights = createFlights(5, "ANY");
        List<FlightDto> filteredFlights = rawFlights.subList(0, 2);

        when(flightSearchPort.searchAllFlights(testRequest)).thenReturn(rawFlights);
        when(filterService.applyFilters(rawFlights, testRequest)).thenReturn(filteredFlights);

        // WHEN
        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        // THEN
        verify(flightSearchPort, times(1)).searchAllFlights(testRequest);
        verify(filterService, times(1)).applyFilters(rawFlights, testRequest);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Returns empty list when no flights are returned")
    void shouldReturnEmptyListWhenPortReturnsEmpty() {
        List<FlightDto> emptyRawFlights = new ArrayList<>();
        when(flightSearchPort.searchAllFlights(testRequest)).thenReturn(emptyRawFlights);
        when(filterService.applyFilters(emptyRawFlights, testRequest)).thenReturn(emptyRawFlights);

        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        verify(flightSearchPort, times(1)).searchAllFlights(testRequest);
        verify(filterService, times(1)).applyFilters(emptyRawFlights, testRequest);
        assertThat(result).isNotNull().isEmpty();
    }

    // ---- Helper ----
    private List<FlightDto> createFlights(int count, String provider) {
        List<FlightDto> flights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDateTime dep = LocalDateTime.now().plusDays(30).withHour(8 + i);
            flights.add(FlightDto.builder()
                    .flightNumber("XX" + (1000 + i))
                    .origin("IST")
                    .destination("COV")
                    .departureDateTime(dep)
                    .arrivalDateTime(dep.plusHours(3))
                    .price(BigDecimal.valueOf(200 + (i * 50)))
                    .provider(provider)
                    .build());
        }
        return flights;
    }
}
