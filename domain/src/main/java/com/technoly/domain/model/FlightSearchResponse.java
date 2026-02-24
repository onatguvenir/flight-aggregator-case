package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Flight search response model returned from REST API.
 *
 * Both "all flights" and "cheapest grouped" services use this response.
 * totalCount is meta information that the client can use for pagination or
 * statistics.
 *
 * Consistent response creation with Static factory methods:
 * - FlightSearchResponse.of(flights): normal response
 * - FlightSearchResponse.empty(): error or no result case
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchResponse {

    /** Returned flight list */
    @Builder.Default
    private List<FlightDto> flights = new ArrayList<>();

    /** Total number of flights (for UI pagination) */
    private int totalCount;

    /** Timestamp when the search was performed */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime searchedAt;

    // ---- Static Factory Methods ----

    /**
     * Successful response: with flight list
     */
    public static FlightSearchResponse of(List<FlightDto> flights) {
        return FlightSearchResponse.builder()
                .flights(flights)
                .totalCount(flights.size())
                .searchedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Empty response: no result from providers or all failed
     */
    public static FlightSearchResponse empty() {
        return FlightSearchResponse.builder()
                .flights(new ArrayList<>())
                .totalCount(0)
                .searchedAt(LocalDateTime.now())
                .build();
    }
}
