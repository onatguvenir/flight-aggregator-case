package com.technoly.infrastructure.adapter;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import com.technoly.domain.port.FlightSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FlightSearchPort implementation: adapter performing provider orchestration.
 *
 * Responsibility:
 * - Combines all {@link FlightProviderPort} implementations injected by Spring
 * (e.g., ProviderA, ProviderB) under a single "search" operation.
 * - Runs provider calls in parallel and gathers results in a single list.
 *
 * Design notes:
 * - {@code List<FlightProviderPort>} injection → to add a new provider,
 * adding a new {@code @Component} implementation is sufficient (OCP).
 * - "Partial success" approach: If one provider fails, results from other
 * providers
 * can still be returned; therefore, it falls back to an empty list on error.
 * - Timeout: A time limit is applied to each provider call so that a stuck
 * provider does not block the entire request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FlightAdapter implements FlightSearchPort {

    private final List<FlightProviderPort> flightProviders;
    /**
     * Thread pool used to parallelize provider calls.
     *
     * Note: In this project example, the adapter creates its own executor.
     * In production systems, it is generally healthier to manage it via
     * {@code @Bean TaskExecutor},
     * leaving shutdown/lifecycle management to Spring.
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public List<FlightDto> searchAllFlights(FlightSearchRequest request) {
        log.info("Parallel SOAP search (Adapter): {} → {}", request.getOrigin(), request.getDestination());

        List<CompletableFuture<List<FlightDto>>> futures = flightProviders.stream()
                .map(provider -> CompletableFuture
                        .supplyAsync(() -> searchWithProvider(provider, request), executorService)
                        // Upper time limit per provider: falls to Exception if exceeded
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            // At this point, the error is swallowed and back to an empty list for "partial
                            // success".
                            // Thus, results from other providers can still be returned.
                            log.error("[{}] error: {}", provider.getProviderName(), ex.getMessage());
                            return new ArrayList<>();
                        }))
                .toList();

        // join(): waits until all futures are complete (either result or empty list)
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    private List<FlightDto> searchWithProvider(FlightProviderPort provider, FlightSearchRequest request) {
        try {
            // Provider implementation itself may be applying Resilience4j
            // (retry/cb/bulkhead).
            // The goal here: to ensure a single provider error doesn't break the entire
            // flow.
            log.info("searchWithProvider Request: {}", request.toString());
            return provider.searchFlights(request);
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {} - Request: {}", provider.getProviderName(), e.getMessage(),
                    request.toString());
            return new ArrayList<>();
        }
    }
}
