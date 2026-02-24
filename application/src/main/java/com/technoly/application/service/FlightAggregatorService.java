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
 * Flight Aggregator Service - Service 1
 *
 * Collects data via FlightSearchPort (Adapter)
 * and applies in-memory filters via FlightFilterService.
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
                log.info("Flight search triggered: {} → {} @ {} [filters: {}]",
                                request.getOrigin(), request.getDestination(), request.getDepartureDate(),
                                request.hasActiveFilters() ? "active" : "none");

                long startTime = System.currentTimeMillis();

                // Get raw (merged from all providers) flights via the interface
                List<FlightDto> allFlights = flightSearchPort.searchAllFlights(request);

                // Apply in-memory filters (Option A)
                List<FlightDto> filtered = flightFilterService.applyFilters(allFlights, request);

                long durationMs = System.currentTimeMillis() - startTime;
                log.info("Search completed: raw={}, filtered={}, duration={}ms",
                                allFlights.size(), filtered.size(), durationMs);

                return filtered;
        }
}
