package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Uçuş Aggregator Servisi - Service 1
 *
 * FlightSearchPort (Adapter) üzerinden verileri toplar
 * ve FlightFilterService ile in-memory filtreleri uygular.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightAggregatorService {

    private final FlightSearchPort flightSearchPort;
    private final FlightFilterService flightFilterService;

    @Cacheable(value = "flightSearch", key = "#request.origin + '_' + #request.destination + '_' + #request.departureDate"
            + " + '_' + #request.priceMin + '_' + #request.priceMax"
            + " + '_' + #request.departureDateFrom + '_' + #request.departureDateTo"
            + " + '_' + #request.arrivalDateFrom + '_' + #request.arrivalDateTo", unless = "#result.isEmpty()")
    public List<FlightDto> searchAllFlights(FlightSearchRequest request) {
        log.info("Uçuş araması tetiklendi: {} → {} @ {} [filtreler: {}]",
                request.getOrigin(), request.getDestination(), request.getDepartureDate(),
                request.hasActiveFilters() ? "aktif" : "yok");

        long startTime = System.currentTimeMillis();

        // Arabirime üzerinden ham (tüm sağlayıcılardan birleştirilmiş) uçuşları al
        List<FlightDto> allFlights = flightSearchPort.searchAllFlights(request);

        // In-memory filtreler uygula (Seçenek A)
        List<FlightDto> filtered = flightFilterService.applyFilters(allFlights, request);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Arama tamamlandı: ham={}, filtrelenmiş={}, süre={}ms",
                allFlights.size(), filtered.size(), durationMs);

        return filtered;
    }
}
