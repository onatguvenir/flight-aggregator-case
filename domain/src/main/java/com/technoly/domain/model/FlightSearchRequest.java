package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flight search request model coming to the REST API.
 *
 * Input validation with Bean Validation (@NotBlank, @NotNull, @Future).
 *
 * --- Mandatory Fields ---
 * origin, destination, departureDate: Mandatory for both endpoints.
 *
 * --- Optional Filter Fields (Option A: in-memory) ---
 * These fields are applied by FlightFilterService after the SOAP call
 * via Stream + Predicate chain. They are not sent to providers.
 *
 * Design Decision (null = no filter):
 * - priceMin = null → no minimum price limit check
 * - departureDateFrom = null → no departure date lower limit check
 * - etc.
 *
 * This "opt-in filter" approach provides backward compatibility:
 * Existing clients continue to work without sending filter parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchRequest {

    // ===========================
    // Mandatory Search Parameters
    // ===========================

    /** Departure airport IATA code (e.g., IST) */
    @NotBlank(message = "Origin cannot be blank")
    private String origin;

    /** Arrival airport IATA code (e.g., COV) */
    @NotBlank(message = "Destination cannot be blank")
    private String destination;

    /** Departure date: must be a future date */
    @NotNull(message = "Departure date cannot be null")
    @Future(message = "Departure date must be a future date")
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDate;

    // ===========================
    // Optional Price Filters
    // ===========================

    /**
     * Minimum price filter (inclusive).
     * If null, lower limit is not applied.
     * 
     * @PositiveOrZero: 0 or positive decimal number expected.
     */
    @PositiveOrZero(message = "Minimum price must be 0 or positive")
    private BigDecimal priceMin;

    /**
     * Maximum price filter (inclusive).
     * If null, upper limit is not applied.
     */
    @PositiveOrZero(message = "Maximum price must be 0 or positive")
    private BigDecimal priceMax;

    // ===========================
    // Optional Date Filters
    // ===========================

    /**
     * Departure time lower limit (inclusive: "from").
     * Flights departing before this value are filtered out.
     * If null, lower limit is not applied.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateFrom;

    /**
     * Departure time upper limit (inclusive: "to").
     * Flights departing after this value are filtered out.
     * If null, upper limit is not applied.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateTo;

    /**
     * Arrival time lower limit (inclusive: "from").
     * Flights arriving before this value are filtered out.
     * If null, lower limit is not applied.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateFrom;

    /**
     * Arrival time upper limit (inclusive: "to").
     * Flights arriving after this value are filtered out.
     * If null, upper limit is not applied.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateTo;

    /**
     * Returns whether this request has any active filters.
     * Useful for cache key generation and log messages.
     *
     * Pure function: does not depend on external state, same input → same output.
     */
    public boolean hasActiveFilters() {
        return priceMin != null || priceMax != null
                || departureDateFrom != null || departureDateTo != null
                || arrivalDateFrom != null || arrivalDateTo != null;
    }
}
