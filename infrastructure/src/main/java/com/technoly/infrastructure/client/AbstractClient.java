package com.technoly.infrastructure.client;

import com.flightprovider.wsdl.SearchRequest;
import com.flightprovider.wsdl.SearchResult;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract class holding common behaviors for SOAP provider clients.
 *
 * The purpose of this class:
 * - Creating SOAP request (domain request → provider request)
 * - Making the SOAP call (marshalSendAndReceive)
 * - Mapping provider response to the common model (SearchResult → FlightDto)
 * - Observability (metrics + logs)
 *
 * Note:
 * - Resilience4j (retry/circuit breaker/bulkhead) decisions are usually
 * defined via annotation in concrete provider classes.
 * - This class standardizes error/metric/log behavior "from a single place".
 */
@Slf4j
abstract class AbstractClient {

    // Formatter object we use to convert date objects to text (serialize)
    // or convert from text to object (deserialize) based on the desired date format
    // (E.g. "01-06-2026T15:00").
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm");

    private final WebServiceTemplate webServiceTemplate;
    private final MeterRegistry meterRegistry;

    protected AbstractClient(WebServiceTemplate webServiceTemplate, MeterRegistry meterRegistry) {
        this.webServiceTemplate = webServiceTemplate;
        this.meterRegistry = meterRegistry;
    }

    protected List<FlightDto> performSearch(FlightSearchRequest request, String providerName) {
        // Defensive Programming: Guard Clauses
        if (request == null) {
            log.warn("[{}] Flight search request cannot be null.", providerName);
            return Collections.emptyList();
        }
        if (request.getOrigin() == null || request.getDestination() == null) {
            log.warn("[{}] Flight origin and destination are mandatory.", providerName);
            return Collections.emptyList();
        }

        log.info("[{}] Flight search (SOAP): {} → {} @ {}",
                providerName, request.getOrigin(), request.getDestination(), request.getDepartureDate());

        // Metric: total number of calls made to the provider
        meterRegistry.counter("flight.search.provider.calls.total", "provider", providerName).increment();

        SearchRequest providerRequest = buildProviderRequest(request);
        Timer.Sample sample = Timer.start(meterRegistry);
        SearchResult result;

        try {
            result = (SearchResult) webServiceTemplate.marshalSendAndReceive(providerRequest);
            // Metric: successful provider calls
            meterRegistry.counter("flight.search.provider.success", "provider", providerName).increment();
        } catch (Exception e) {
            // Metric: provider error count
            meterRegistry.counter("flight.search.provider.errors", "provider", providerName).increment();
            log.error("[{}] SOAP communication error: {}", providerName, e.getMessage());

            // Throwing the Exception upwards is important:
            // - Resilience4j annotations can see this exception
            // and apply retry/circuit-breaker/fallback decisions.
            throw e;
        } finally {
            // Metric: provider call durations (latency)
            sample.stop(meterRegistry.timer("flight.search.provider.latency", "provider", providerName));
        }

        return mapToFlightDtos(result, providerName);
    }

    protected List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable,
            String providerName) {
        log.warn("[{}] Fallback active. Origin: {}, Error: {}", providerName,
                Optional.ofNullable(request).map(FlightSearchRequest::getOrigin).orElse("Unknown"),
                throwable.getMessage());
        return Collections.emptyList();
    }

    private SearchRequest buildProviderRequest(FlightSearchRequest request) {
        SearchRequest req = new SearchRequest();
        req.setOrigin(request.getOrigin());
        req.setDestination(request.getDestination());

        // Functional approach: if-not-null check with Optional
        Optional.ofNullable(request.getDepartureDate())
                .ifPresent(date -> req.setDepartureDate(date.format(DATE_FORMATTER)));

        return req;
    }

    private List<FlightDto> mapToFlightDtos(SearchResult result, String providerName) {
        return Optional.ofNullable(result)
                .filter(res -> {
                    if (res.isHasError()) {
                        log.warn("[{}] Provider returned error: {}", providerName, res.getErrorMessage());
                        return false;
                    }
                    return true;
                })
                .map(SearchResult::getFlights)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(java.util.Objects::nonNull) // Defensive: Eliminated possible null elements in the list
                .map(flight -> FlightDto.builder()
                        .flightNumber(Optional.ofNullable(flight.getFlightNumber()).orElse("UNKNOWN"))
                        .origin(Optional.ofNullable(flight.getOrigin()).orElse("UNKNOWN"))
                        .destination(Optional.ofNullable(flight.getDestination()).orElse("UNKNOWN"))
                        .departureDateTime(parseDateTime(flight.getDepartureTime()))
                        .arrivalDateTime(parseDateTime(flight.getArrivalTime()))
                        .price(Optional.ofNullable(flight.getPrice()).orElse(BigDecimal.ZERO))
                        .provider(providerName)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Converts the date information in String format from the flight provider (SOAP
     * Service)
     * back to Java's LocalDateTime object so that it can be used universally
     * within our application.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            // If the external provider returns an incorrect or empty date, to prevent
            // the system from crashing, it returns the current date as a temporary
            // or backup (fallback) value.
            return LocalDateTime.now(); // Fallback if provider returns bad date
        }
        // We parse and split the incoming String date according to our
        // specified "dd-MM-yyyy'T'HH:mm" format rule.
        return LocalDateTime.parse(dateTimeStr, DATE_FORMATTER);
    }
}
