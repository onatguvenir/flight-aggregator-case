package com.technoly.infrastructure.client;

import com.flightprovidera.service.SearchRequest;
import com.flightprovidera.service.SearchResult;
import com.flightprovidera.service.SearchService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FlightProvider A — Java Kütüphane Adaptörü
 *
 * FlightProviderA, bir HTTP/SOAP servisi DEĞİL, plain Java kütüphanesidir.
 * Bu adapter (Adapter Pattern), domain'in FlightProviderPort interface'ini
 * ProviderA'nın SearchService API'sine uyarlar.
 *
 * Spring DI kullanarak SearchService ve MeterRegistry (Micrometer
 * Observability) bağımlılıklarını yönetiriz (SOLID - DIP).
 */
@Slf4j
@Component
public class FlightProviderAClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_A";

    private final SearchService searchService;
    private final MeterRegistry meterRegistry;

    /**
     * Constructor Injection
     * 
     * @param searchService Provider'a özel state-less kütüphane servisi (@Qualifier
     *                      ile ayırt edilir)
     * @param meterRegistry Metrik toplama (Prometheus / OpenTelemetry için)
     */
    public FlightProviderAClient(
            @Qualifier("providerASearchService") SearchService searchService,
            MeterRegistry meterRegistry) {
        this.searchService = searchService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @CircuitBreaker(name = "providerA", fallbackMethod = "fallbackSearchFlights")
    @Retry(name = "providerA")
    @Bulkhead(name = "providerA", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackSearchFlights")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        log.info("[ProviderA] Uçuş araması: {} → {} @ {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        SearchRequest providerRequest = buildProviderRequest(request);

        // Metrik tayini (observability)
        Timer.Sample sample = Timer.start(meterRegistry);

        SearchResult result;
        try {
            // Direkt Java method call
            result = searchService.availabilitySearch(providerRequest);
        } finally {
            sample.stop(meterRegistry.timer("flight.search.provider.latency", "provider", PROVIDER_NAME));
        }

        return mapToFlightDtos(result);
    }

    /**
     * Fallback method: CircuitBreaker açıkken veya tüm retry'lar tükenince
     * çağrılır.
     * Null Object Pattern kullanarak empty list döner (application null check
     * yapmaz).
     */
    public List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable) {
        log.warn("[ProviderA] Fallback devrede. Hata: {}", throwable.getMessage());
        return new ArrayList<>();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private SearchRequest buildProviderRequest(FlightSearchRequest request) {
        return new SearchRequest(
                request.getOrigin(),
                request.getDestination(),
                request.getDepartureDate());
    }

    private List<FlightDto> mapToFlightDtos(SearchResult result) {
        if (result == null) {
            log.warn("[ProviderA] SearchResult null döndü");
            return new ArrayList<>();
        }

        if (result.isHasError()) {
            log.warn("[ProviderA] Provider hata döndürdü: {}", result.getErrorMessage());
            return new ArrayList<>();
        }

        return Optional.ofNullable(result.getFlightOptions())
                .orElse(new ArrayList<>())
                .stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNo())
                        .origin(flight.getOrigin())
                        .destination(flight.getDestination())
                        .departureDateTime(flight.getDeparturedatetime())
                        .arrivalDateTime(flight.getArrivaldatetime())
                        .price(Optional.ofNullable(flight.getPrice()).orElse(BigDecimal.ZERO))
                        .provider(PROVIDER_NAME)
                        .build())
                .collect(Collectors.toList());
    }
}
