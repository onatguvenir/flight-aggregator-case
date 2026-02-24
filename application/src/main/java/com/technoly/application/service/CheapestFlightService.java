package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cheapest Flight Service - Service 2
 *
 * Flow:
 * 1. Get all (raw, unfiltered) flights from FlightAggregatorService
 * 2. Group by composite key (flightNumber, origin, dest, depDT, arrDT)
 * 3. Select the cheapest from each group (minBy price)
 * 4. Return the list with all filters applied, including price filter
 *
 * Filter application order (important!):
 * Grouping is done FIRST (cheapest semantics preserved),
 * price/date filters are applied LATER.
 *
 * Why a separate @Cacheable?
 * "cheapestFlights" cache returns a different result with the same key
 * compared to "flightSearch" cache (grouped vs all). A separate namespace is
 * required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheapestFlightService {

        private final FlightAggregatorService flightAggregatorService;
        private final FlightFilterService flightFilterService;

        @Cacheable(value = "cheapestFlights", key = "#request.origin + '_' + #request.destination + '_' + #request.departureDate"
                        + " + '_' + #request.priceMin + '_' + #request.priceMax"
                        + " + '_' + #request.departureDateFrom + '_' + #request.departureDateTo"
                        + " + '_' + #request.arrivalDateFrom + '_' + #request.arrivalDateTo", unless = "#result.isEmpty()")
        public List<FlightDto> findCheapestFlights(FlightSearchRequest request) {
                log.info("Cheapest flight search: {} → {} @ {} [filters: {}]",
                                request.getOrigin(), request.getDestination(), request.getDepartureDate(),
                                request.hasActiveFilters() ? "active" : "none");

                // Step 1: Get all raw flights (FlightAggregatorService applies its own cache +
                // filter)
                List<FlightDto> allFlights = flightAggregatorService.searchAllFlights(request);

                if (allFlights.isEmpty()) {
                        return List.of();
                }

                // Step 2 & 3: Group and select the cheapest from each group
                // nullsLast: flights without price fall to the end in grouping/sorting
                Comparator<BigDecimal> nullSafePrice = Comparator.nullsLast(Comparator.naturalOrder());

                List<FlightDto> cheapestFlights = allFlights.stream()
                                .collect(Collectors.groupingBy(
                                                FlightGroupKey::of,
                                                Collectors.minBy(Comparator.comparing(
                                                                FlightDto::getPrice,
                                                                nullSafePrice))))
                                .values()
                                .stream()
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .sorted(Comparator.comparing(FlightDto::getPrice, nullSafePrice))
                                .collect(Collectors.toList());

                log.info("Grouping: {}→{} flights (from filtered raw list)",
                                allFlights.size(), cheapestFlights.size());

                return cheapestFlights;
        }

        /**
         * Composite Grouping Key (Java Record)
         *
         * Immutable, equals/hashCode automatically generated.
         * If (flightNo + origin + dest + depDT + arrDT) is the same, it's the same
         * group.
         */
        private record FlightGroupKey(
                        String flightNumber,
                        String origin,
                        String destination,
                        LocalDateTime departureDateTime,
                        LocalDateTime arrivalDateTime) {
                static FlightGroupKey of(FlightDto flight) {
                        return new FlightGroupKey(
                                        flight.getFlightNumber(),
                                        flight.getOrigin(),
                                        flight.getDestination(),
                                        flight.getDepartureDateTime(),
                                        flight.getArrivalDateTime());
                }
        }
}
