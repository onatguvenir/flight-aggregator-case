package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Flight Filter Service
 *
 * Applies the optional filter parameters from FlightSearchRequest
 * (priceMin, priceMax, departureDateFrom, departureDateTo,
 * arrivalDateFrom, arrivalDateTo) to the flight list.
 *
 * ---- Design Principles ----
 *
 * 1. Pure Function (Functional Approach):
 * applyFilters() does not change external state, returns a new list.
 * It does not mutate the input list. → Thread-safe, easy to test.
 *
 * 2. Composable Predicate Chain (Strategy Pattern):
 * Each filter is an independent Predicate<FlightDto>.
 * A chain is built with Predicate.and(); null predicates are skipped.
 * When a new filter is needed, only a new Predicate is added (OCP).
 *
 * 3. Null = "No filter" Semantics:
 * If the parameter is null, Predicate.alwaysTrue() is returned (guard clause).
 * In this way, each parameter can be opted-in independently.
 *
 * 4. Defensive (Null-safe) Comparison:
 * FlightDto fields can be null (not provided by the provider).
 * If there is a null field, that predicate returns false (flight is filtered).
 *
 * Example:
 * applyFilters(flights, req.priceMin=200, req.priceMax=400)
 * → only flights with 200 ≤ price ≤ 400 are returned
 */
@Slf4j
@Service
class FlightFilterService {

    /**
     * Applies all active filters to the flight list.
     *
     * @param flights List of flights to be filtered
     * @param request Search request containing filter parameters
     * @return New filtered list (original list is unchanged)
     */
    List<FlightDto> applyFilters(List<FlightDto> flights, FlightSearchRequest request) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        if (!request.hasActiveFilters()) {
            // Avoid Stream overhead if there are no filters at all
            return flights;
        }

        // Build Composable Predicate chain
        // Each predicate is protected by a null-safe guard clause
        Predicate<FlightDto> combinedFilter = buildPricePredicate(request)
                .and(buildDepartureDateFromPredicate(request))
                .and(buildDepartureDateToPredicate(request))
                .and(buildArrivalDateFromPredicate(request))
                .and(buildArrivalDateToPredicate(request));

        List<FlightDto> filtered = flights.stream()
                .filter(combinedFilter)
                .collect(Collectors.toList());

        log.debug("Filter applied: {} → {} flights (filters: {})",
                flights.size(), filtered.size(), describeFilters(request));

        return filtered;
    }

    // ---- Private Predicate Builders ----

    /**
     * Price range predicate (priceMin and priceMax are handled together).
     * priceMin null → no lower limit; priceMax null → no upper limit.
     * Flight.price null → flight is filtered (defensive).
     */
    private Predicate<FlightDto> buildPricePredicate(FlightSearchRequest req) {
        return flight -> {
            if (flight.getPrice() == null) {
                // Flight with unknown price is eliminated if there is a price filter.
                return req.getPriceMin() == null && req.getPriceMax() == null;
            }
            boolean minOk = req.getPriceMin() == null
                    || flight.getPrice().compareTo(req.getPriceMin()) >= 0;
            boolean maxOk = req.getPriceMax() == null
                    || flight.getPrice().compareTo(req.getPriceMax()) <= 0;
            return minOk && maxOk;
        };
    }

    /**
     * Departure time lower limit: departureDateTime >= departureDateFrom
     */
    private Predicate<FlightDto> buildDepartureDateFromPredicate(FlightSearchRequest req) {
        if (req.getDepartureDateFrom() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getDepartureDateTime() != null
                && !flight.getDepartureDateTime().isBefore(req.getDepartureDateFrom());
    }

    /**
     * Departure time upper limit: departureDateTime <= departureDateTo
     */
    private Predicate<FlightDto> buildDepartureDateToPredicate(FlightSearchRequest req) {
        if (req.getDepartureDateTo() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getDepartureDateTime() != null
                && !flight.getDepartureDateTime().isAfter(req.getDepartureDateTo());
    }

    /**
     * Arrival time lower limit: arrivalDateTime >= arrivalDateFrom
     */
    private Predicate<FlightDto> buildArrivalDateFromPredicate(FlightSearchRequest req) {
        if (req.getArrivalDateFrom() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getArrivalDateTime() != null
                && !flight.getArrivalDateTime().isBefore(req.getArrivalDateFrom());
    }

    /**
     * Arrival time upper limit: arrivalDateTime <= arrivalDateTo
     */
    private Predicate<FlightDto> buildArrivalDateToPredicate(FlightSearchRequest req) {
        if (req.getArrivalDateTo() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getArrivalDateTime() != null
                && !flight.getArrivalDateTime().isAfter(req.getArrivalDateTo());
    }

    /** Summarize active filters for log description */
    private String describeFilters(FlightSearchRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getPriceMin() != null)
            sb.append("priceMin=").append(req.getPriceMin()).append(" ");
        if (req.getPriceMax() != null)
            sb.append("priceMax=").append(req.getPriceMax()).append(" ");
        if (req.getDepartureDateFrom() != null)
            sb.append("depFrom=").append(req.getDepartureDateFrom()).append(" ");
        if (req.getDepartureDateTo() != null)
            sb.append("depTo=").append(req.getDepartureDateTo()).append(" ");
        if (req.getArrivalDateFrom() != null)
            sb.append("arrFrom=").append(req.getArrivalDateFrom()).append(" ");
        if (req.getArrivalDateTo() != null)
            sb.append("arrTo=").append(req.getArrivalDateTo()).append(" ");
        return sb.toString().trim();
    }
}
