package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Uçuş Aggregator Servisi - Service 1
 *
 * Her iki sağlayıcıdan (ProviderA ve ProviderB) paralel olarak uçuş
 * verisi toplar, birleştirir ve opsiyonel filtreleri uygular.
 *
 * Akış:
 * 1. CompletableFuture ile her iki provider'a paralel SOAP çağrısı
 * 2. Sonuçlar birleştirilir (flatMap)
 * 3. FlightFilterService ile in-memory filtreler uygulanır
 * 4. Sonuç @Cacheable ile Redis'e yazılır
 *
 * @Cacheable cache key'i artık tüm filtre parametrelerini içerir:
 *            Farklı filtreler farklı cache entry'leri oluşturur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightAggregatorService {

    /**
     * Strategy Pattern: Tüm FlightProviderPort implementasyonları inject edilir.
     */
    private final List<FlightProviderPort> flightProviders;

    /**
     * Uçuş filtrelerini uygulayan servis (in-memory Predicate chain).
     */
    private final FlightFilterService flightFilterService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /**
     * Tüm sağlayıcılardan paralel uçuş araması ve filtreleme.
     *
     * Cache key stratejisi:
     * - Zorunlu alanlar: origin, destination, departureDate
     * - Opsiyonel filtreler: priceMin, priceMax, depFrom, depTo, arrFrom, arrTo
     * Her filtre kombinasyonu ayrı bir cache entry'sidir.
     *
     * unless="#result.isEmpty()": Boş sonuçları cache'leme.
     */
    @Cacheable(value = "flightSearch", key = "#request.origin + '_' + #request.destination + '_' + #request.departureDate"
            + " + '_' + #request.priceMin + '_' + #request.priceMax"
            + " + '_' + #request.departureDateFrom + '_' + #request.departureDateTo"
            + " + '_' + #request.arrivalDateFrom + '_' + #request.arrivalDateTo", unless = "#result.isEmpty()")
    public List<FlightDto> searchAllFlights(FlightSearchRequest request) {
        log.info("Paralel SOAP araması: {} → {} @ {} [filtreler: {}]",
                request.getOrigin(), request.getDestination(), request.getDepartureDate(),
                request.hasActiveFilters() ? "aktif" : "yok");

        long startTime = System.currentTimeMillis();

        // Her sağlayıcı için asenkron task
        List<CompletableFuture<List<FlightDto>>> futures = flightProviders.stream()
                .map(provider -> CompletableFuture
                        .supplyAsync(() -> searchWithProvider(provider, request), executorService)
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.error("[{}] hata: {}", provider.getProviderName(), ex.getMessage());
                            return new ArrayList<>();
                        }))
                .collect(Collectors.toList());

        // Future'ları birleştir
        List<FlightDto> allFlights = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // In-memory filtreler uygula (Seçenek A)
        List<FlightDto> filtered = flightFilterService.applyFilters(allFlights, request);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Arama tamamlandı: ham={}, filtrelenmiş={}, süre={}ms",
                allFlights.size(), filtered.size(), durationMs);

        return filtered;
    }

    private List<FlightDto> searchWithProvider(FlightProviderPort provider, FlightSearchRequest request) {
        try {
            return provider.searchFlights(request);
        } catch (Exception e) {
            log.error("[{}] Beklenmeyen hata: {}", provider.getProviderName(), e.getMessage());
            return new ArrayList<>();
        }
    }
}
