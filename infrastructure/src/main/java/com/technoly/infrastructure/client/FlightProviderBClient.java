package com.technoly.infrastructure.client;

import com.flightproviderb.service.SearchRequest;
import com.flightproviderb.service.SearchResult;
import com.flightproviderb.service.SearchService;
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
 * FlightProvider B — Java Kütüphane Adaptörü
 *
 * ProviderA ile aynı mimari, temel farklar Anti-Corruption layer mantığında
 * barınır.
 * Spring DI kullanarak SearchService ve MeterRegistry (Micrometer
 * Observability) bağımlılıklarını yönetiriz (SOLID - DIP).
 */
@Slf4j
@Component
public class FlightProviderBClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_B";

    private final SearchService searchService;
    private final MeterRegistry meterRegistry;

    /**
     * Constructor Injection
     * 
     * @param searchService Provider'a özel state-less kütüphane servisi (@Qualifier
     *                      ile ayırt edilir)
     * @param meterRegistry Metrik toplama
     */
    public FlightProviderBClient(
            @Qualifier("providerBSearchService") SearchService searchService,
            MeterRegistry meterRegistry) {
        this.searchService = searchService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @CircuitBreaker(name = "providerB", fallbackMethod = "fallbackSearchFlights")
    @Retry(name = "providerB")
    @Bulkhead(name = "providerB", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallbackSearchFlights")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        log.info("[ProviderB] Uçuş araması: {} → {} @ {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        // Anti-Corruption Layer: origin→departure, destination→arrival
        SearchRequest providerRequest = buildProviderRequest(request);

        // Metrik tayini
        Timer.Sample sample = Timer.start(meterRegistry);

        SearchResult result;
        try {
            result = searchService.availabilitySearch(providerRequest);
        } finally {
            sample.stop(meterRegistry.timer("flight.search.provider.latency", "provider", PROVIDER_NAME));
        }

        return mapToFlightDtos(result);
    }

    /**
     * Fallback: Tüm retry'lar tükendikten sonra veya circuit açıkken çağrılır.
     * Null Object Pattern kullanıldı.
     */
    public List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable) {
        log.warn("[ProviderB] Fallback devrede. Hata: {}", throwable.getMessage());
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
            log.warn("[ProviderB] SearchResult null döndü");
            return new ArrayList<>();
        }

        if (result.isHasError()) {
            log.warn("[ProviderB] Provider hata döndürdü: {}", result.getErrorMessage());
            return new ArrayList<>();
        }

        return Optional.ofNullable(result.getFlightOptions())
                .orElse(new ArrayList<>())
                .stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNumber())
                        .origin(flight.getDeparture()) // Anti-Corruption
                        .destination(flight.getArrival()) // Anti-Corruption
                        .departureDateTime(flight.getDeparturedatetime())
                        .arrivalDateTime(flight.getArrivaldatetime())
                        .price(Optional.ofNullable(flight.getPrice()).orElse(BigDecimal.ZERO))
                        .provider(PROVIDER_NAME)
                        .build())
                .collect(Collectors.toList());
    }
}
