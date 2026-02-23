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
 * Adapter that implements FlightSearchPort by orchestrating multiple
 * FlightProviderPorts.
 * Follows the Open/Closed Principle (OCP) by injecting a List of providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FlightAdapter implements FlightSearchPort {

    private final List<FlightProviderPort> flightProviders;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public List<FlightDto> searchAllFlights(FlightSearchRequest request) {
        log.info("Paralel SOAP araması (Adapter): {} → {}", request.getOrigin(), request.getDestination());

        List<CompletableFuture<List<FlightDto>>> futures = flightProviders.stream()
                .map(provider -> CompletableFuture
                        .supplyAsync(() -> searchWithProvider(provider, request), executorService)
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.error("[{}] hata: {}", provider.getProviderName(), ex.getMessage());
                            return new ArrayList<>();
                        }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
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
