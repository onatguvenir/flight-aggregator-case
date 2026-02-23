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
class FlightProviderBClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;

    private SimpleMeterRegistry meterRegistry;

    private FlightProviderBClient client;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        client = new FlightProviderBClient(webServiceTemplate, meterRegistry);
    }

    @Test
    void searchFlights_ShouldReturnFlights_WhenProviderReturnsSuccess() {
        // Arrange
        LocalDateTime departureDate = LocalDateTime.now().plusDays(2);
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(departureDate)
                .build();

        Flight mockFlight = new Flight();
        mockFlight.setFlightNumber("B456");
        mockFlight.setOrigin("IST");
        mockFlight.setDestination("LHR");
        // Sınıf özelliklerinde (Mock) DATE_FORMATTER kullanarak gelen LocalDateTime
        // nesnesini,
        // SOAP servisin beklentisi olan dd-MM-yyyy'T'HH:mm metnine dönüştürüyoruz.
        DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm");
        mockFlight.setDepartureTime(departureDate.format(DATE_FORMATTER));
        mockFlight.setArrivalTime(departureDate.plusHours(4).format(DATE_FORMATTER));
        mockFlight.setPrice(new BigDecimal("180.00"));

        SearchResult mockResult = new SearchResult();
        mockResult.setHasError(false);
        mockResult.getFlights().add(mockFlight);

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(mockResult);

        // Act
        List<FlightDto> result = client.searchFlights(request);

        // Assert
        assertThat(result).hasSize(1);
        FlightDto flightDto = result.get(0);
        assertThat(flightDto.getFlightNumber()).isEqualTo("B456");
        assertThat(flightDto.getOrigin()).isEqualTo("IST");
        assertThat(flightDto.getDestination()).isEqualTo("LHR");
        assertThat(flightDto.getPrice()).isEqualTo(new BigDecimal("180.00"));
        assertThat(flightDto.getProvider()).isEqualTo("PROVIDER_B");

        // Verify that adapter maps fields correctly to the WebServiceTemplate payload
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(webServiceTemplate).marshalSendAndReceive(captor.capture());
        SearchRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getOrigin()).isEqualTo("IST");
        assertThat(capturedRequest.getDestination()).isEqualTo("LHR");
        // Adapter üzerinden üretilen SOAP isteğinin tarihlerinin de aynı şekilde
        // DATE_FORMATTER ile
        // metne doğru dönüştürülüp gönderildiğini doğruluyoruz.
        assertThat(capturedRequest.getDepartureDate())
                .isEqualTo(departureDate.format(DATE_FORMATTER));

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
                .departureDate(LocalDateTime.now().plusDays(2))
                .build();

        SearchResult mockResult = new SearchResult();
        mockResult.setHasError(true);
        mockResult.setErrorMessage("Service Unavailable");

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
                .departureDate(LocalDateTime.now().plusDays(2))
                .build();

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(null);

        // Act
        List<FlightDto> result = client.searchFlights(request);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void fallback_ShouldReturnEmptyList() {
        // Arrange
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .departureDate(LocalDateTime.now().plusDays(2))
                .build();

        // Act
        List<FlightDto> result = client.fallback(request, new RuntimeException("Circuit Breaker Open"));

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void searchFlights_ShouldReturnEmptyList_WhenRequestIsNull() {
        List<FlightDto> result = client.searchFlights(null);
        assertThat(result).isEmpty();
    }

    @Test
    void searchFlights_ShouldReturnEmptyList_WhenOriginIsNull() {
        FlightSearchRequest request = FlightSearchRequest.builder().destination("LHR").build();
        List<FlightDto> result = client.searchFlights(request);
        assertThat(result).isEmpty();
    }

    @Test
    void searchFlights_ShouldReturnEmptyList_WhenDestinationIsNull() {
        FlightSearchRequest request = FlightSearchRequest.builder().origin("IST").build();
        List<FlightDto> result = client.searchFlights(request);
        assertThat(result).isEmpty();
    }

    @Test
    void searchFlights_ShouldHandleNullDepartureDate() {
        FlightSearchRequest request = FlightSearchRequest.builder().origin("IST").destination("LHR").build();
        SearchResult mockResult = new SearchResult();
        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(mockResult);

        List<FlightDto> result = client.searchFlights(request);
        assertThat(result).isEmpty();
    }

    @Test
    void mapToFlightDtos_ShouldHandleNullOrEmptyDateTime() {
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST")
                .destination("LHR")
                .build();

        SearchResult mockResult = new SearchResult();
        Flight mockFlight1 = new Flight();
        mockFlight1.setDepartureTime(null);
        mockFlight1.setArrivalTime("");
        mockResult.getFlights().add(mockFlight1);

        when(webServiceTemplate.marshalSendAndReceive(any(SearchRequest.class))).thenReturn(mockResult);

        List<FlightDto> result = client.searchFlights(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDepartureDateTime()).isNotNull();
        assertThat(result.get(0).getArrivalDateTime()).isNotNull();
    }
}
