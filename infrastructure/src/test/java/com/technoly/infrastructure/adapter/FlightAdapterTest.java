package com.technoly.infrastructure.adapter;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
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
 * FlightAdapter Unit Test
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlightAdapter Unit Tests")
class FlightAdapterTest {

    @Mock
    private FlightProviderPort providerA;

    @Mock
    private FlightProviderPort providerB;

    private FlightAdapter flightAdapter;

    private FlightSearchRequest testRequest;

    @BeforeEach
    void setUp() {
        flightAdapter = new FlightAdapter(List.of(providerA, providerB));

        testRequest = FlightSearchRequest.builder()
                .origin("IST")
                .destination("COV")
                .departureDate(LocalDateTime.now().plusDays(30))
                .build();

        when(providerA.getProviderName()).thenReturn("PROVIDER_A");
        when(providerB.getProviderName()).thenReturn("PROVIDER_B");
    }

    @Test
    @DisplayName("Should call both providers exactly once")
    void shouldCallBothProvidersOnce() {
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(new ArrayList<>());

        flightAdapter.searchAllFlights(testRequest);

        verify(providerA, times(1)).searchFlights(testRequest);
        verify(providerB, times(1)).searchFlights(testRequest);
    }

    @Test
    @DisplayName("Should merge results from both providers")
    void shouldMergeFlightsFromBothProviders() {
        List<FlightDto> providerAFlights = createFlights(3, "PROVIDER_A");
        List<FlightDto> providerBFlights = createFlights(4, "PROVIDER_B");

        when(providerA.searchFlights(testRequest)).thenReturn(providerAFlights);
        when(providerB.searchFlights(testRequest)).thenReturn(providerBFlights);

        List<FlightDto> result = flightAdapter.searchAllFlights(testRequest);

        assertThat(result).hasSize(7);
    }

    @Test
    @DisplayName("Should return results from one provider when the other returns empty")
    void shouldReturnResultsWhenOneProviderReturnsEmpty() {
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(createFlights(5, "PROVIDER_B"));

        List<FlightDto> result = flightAdapter.searchAllFlights(testRequest);

        assertThat(result).hasSize(5);
        assertThat(result).allMatch(f -> "PROVIDER_B".equals(f.getProvider()));
    }

    @Test
    @DisplayName("Should return results from other provider when one throws exception")
    void shouldReturnOtherProviderResultsWhenOneThrowsException() {
        when(providerA.searchFlights(testRequest)).thenThrow(new RuntimeException("ProviderA is down"));
        when(providerB.searchFlights(testRequest)).thenReturn(createFlights(3, "PROVIDER_B"));

        List<FlightDto> result = flightAdapter.searchAllFlights(testRequest);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Should return empty list when both providers return empty")
    void shouldReturnEmptyListWhenBothProvidersReturnEmpty() {
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(new ArrayList<>());

        List<FlightDto> result = flightAdapter.searchAllFlights(testRequest);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Future timeout should handle exception and return empty list")
    void shouldHandleTimeoutFromFutures() {
        when(providerA.searchFlights(testRequest)).thenAnswer(invocation -> {
            Thread.sleep(15000); // Trigger orTimeout(10, TimeUnit.SECONDS)
            return createFlights(1, "PROVIDER_A");
        });
        when(providerB.searchFlights(testRequest)).thenReturn(createFlights(1, "PROVIDER_B"));

        List<FlightDto> result = flightAdapter.searchAllFlights(testRequest);

        assertThat(result).hasSize(1);
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
