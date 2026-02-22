package com.technoly.infrastructure.client;

import com.flightprovider.wsdl.Flight;
import com.flightprovider.wsdl.SearchRequest;
import com.flightprovider.wsdl.SearchResult;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FlightProvider B — SOAP Web Service Client
 */
@Slf4j
@Component
public class FlightProviderBClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_B";

    private final WebServiceTemplate webServiceTemplate;
    private final MeterRegistry meterRegistry;

    public FlightProviderBClient(
            @Qualifier("webServiceTemplateB") WebServiceTemplate webServiceTemplate,
            MeterRegistry meterRegistry) {
        this.webServiceTemplate = webServiceTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @CircuitBreaker(name = "providerB", fallbackMethod = "fallbackSearchFlights")
    @Retry(name = "providerB")
    @Bulkhead(name = "providerB", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackSearchFlights")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        log.info("[ProviderB] Uçuş araması (SOAP): {} → {} @ {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        SearchRequest providerRequest = buildProviderRequest(request);

        Timer.Sample sample = Timer.start(meterRegistry);

        SearchResult result;
        try {
            result = (SearchResult) webServiceTemplate.marshalSendAndReceive(providerRequest);
        } finally {
            sample.stop(meterRegistry.timer("flight.search.provider.latency", "provider", PROVIDER_NAME));
        }

        return mapToFlightDtos(result);
    }

    public List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable) {
        log.warn("[ProviderB] Fallback devrede. Hata: {}", throwable.getMessage());
        return new ArrayList<>();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private SearchRequest buildProviderRequest(FlightSearchRequest request) {
        SearchRequest req = new SearchRequest();
        req.setOrigin(request.getOrigin());
        req.setDestination(request.getDestination());
        req.setDepartureDate(request.getDepartureDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return req;
    }

    private List<FlightDto> mapToFlightDtos(SearchResult result) {
        if (result == null) {
            log.warn("[ProviderB] SearchResult null döndü");
            return new ArrayList<>();
        }

        if (result.isHasError()) {
            log.warn("[ProviderB] Provider hata döndürdü: {}", result.getErrorMessage());
            return new ArrayList<>();
        }

        return result.getFlights().stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNumber())
                        .origin(flight.getOrigin())
                        .destination(flight.getDestination())
                        .departureDateTime(
                                LocalDateTime.parse(flight.getDepartureTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .arrivalDateTime(
                                LocalDateTime.parse(flight.getArrivalTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .price(Optional.ofNullable(flight.getPrice()).orElse(BigDecimal.ZERO))
                        .provider(PROVIDER_NAME)
                        .build())
                .collect(Collectors.toList());
    }
}
