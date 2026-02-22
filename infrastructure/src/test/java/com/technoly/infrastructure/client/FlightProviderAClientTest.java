package com.technoly.infrastructure.client;

import com.flightprovider.wsdl.Flight;
import com.flightprovider.wsdl.SearchRequest;
import com.flightprovider.wsdl.SearchResult;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightProviderAClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;

    private SimpleMeterRegistry meterRegistry;

    private FlightProviderAClient client;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        client = new FlightProviderAClient(webServiceTemplate, meterRegistry);
    }

    @Test
    void searchFlights_ShouldReturnFlights_WhenProviderReturnsSuccess() {
        // Arrange
        LocalDateTime departureDate = LocalDateTime.now().plusDays(1);
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(departureDate)
                .build();

        Flight mockFlight = new Flight();
        mockFlight.setFlightNumber("A123");
        mockFlight.setOrigin("IST");
        mockFlight.setDestination("LHR");
        mockFlight.setDepartureTime(departureDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        mockFlight.setArrivalTime(departureDate.plusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        mockFlight.setPrice(new BigDecimal("150.00"));

        SearchResult mockResult = new SearchResult();
        mockResult.setHasError(false);
        mockResult.getFlights().add(mockFlight);

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(mockResult);

        // Act
        List<FlightDto> result = client.searchFlights(request);

        // Assert
        assertThat(result).hasSize(1);
        FlightDto flightDto = result.get(0);
        assertThat(flightDto.getFlightNumber()).isEqualTo("A123");
        assertThat(flightDto.getOrigin()).isEqualTo("IST");
        assertThat(flightDto.getDestination()).isEqualTo("LHR");
        assertThat(flightDto.getPrice()).isEqualTo(new BigDecimal("150.00"));
        assertThat(flightDto.getProvider()).isEqualTo("PROVIDER_A");

        // Verify that the adapter constructs the external request correctly
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(webServiceTemplate).marshalSendAndReceive(captor.capture());
        SearchRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getOrigin()).isEqualTo("IST");
        assertThat(capturedRequest.getDestination()).isEqualTo("LHR");
        assertThat(capturedRequest.getDepartureDate())
                .isEqualTo(departureDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Verify metrics
        assertThat(meterRegistry.find("flight.search.provider.latency").timer()).isNotNull();
        assertThat(meterRegistry.find("flight.search.provider.latency").timer().count()).isEqualTo(1);
    }

    @Test
    void searchFlights_ShouldReturnEmptyList_WhenProviderReturnsError() {
        // Arrange
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(LocalDateTime.now().plusDays(1))
                .build();

        SearchResult mockResult = new SearchResult();
        mockResult.setHasError(true);
        mockResult.setErrorMessage("Internal Server Error");

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(mockResult);

        // Act
        List<FlightDto> result = client.searchFlights(request);

        // Assert
        assertThat(result).isEmpty();
        verify(webServiceTemplate, times(1)).marshalSendAndReceive(any(SearchRequest.class));
    }

    @Test
    void searchFlights_ShouldReturnEmptyList_WhenResultIsNull() {
        // Arrange
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(LocalDateTime.now().plusDays(1))
                .build();

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(null);

        // Act
        List<FlightDto> result = client.searchFlights(request);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void fallbackSearchFlights_ShouldReturnEmptyList() {
        // Arrange
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(LocalDateTime.now().plusDays(1))
                .build();

        // Act
        List<FlightDto> result = client.fallbackSearchFlights(request, new RuntimeException("Circuit Breaker Open"));

        // Assert
        assertThat(result).isEmpty();
    }
}
