package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flight Data Transfer Object (DTO)
 *
 * This class is used to normalize flight data coming from both ProviderA
 * and ProviderB. Different field names from both providers (origin/destination
 * vs departure/arrival) are combined in this common model.
 *
 * @Builder: Object creation via Fluent API (for null safety)
 * @Data: @Getter, @Setter, @ToString, @EqualsAndHashCode (Lombok)
 *
 *        Why DTO? Using the same class with Entity (DB) classes
 *        violates SRP. DTO only transfers data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightDto {

    /** Flight number: TK1001, PC1002 etc. — part of the grouping key */
    private String flightNumber;

    /** Departure airport IATA code: IST, SAW etc. */
    private String origin;

    /** Arrival airport IATA code: COV, LHR etc. */
    private String destination;

    /** Departure date/time — part of the grouping key */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateTime;

    /** Arrival date/time — part of the grouping key */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateTime;

    /** Flight price — used for cheapest selection */
    private BigDecimal price;

    /**
     * Indicates the source: "PROVIDER_A" or "PROVIDER_B"
     * Used for logging and monitoring.
     */
    private String provider;
}
