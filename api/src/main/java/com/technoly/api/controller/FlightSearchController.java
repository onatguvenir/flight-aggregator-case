package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.application.service.CheapestFlightService;
import com.technoly.application.service.FlightAggregatorService;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.model.FlightSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flight Search REST Controller
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Tag(name = "Flight Search", description = "Flight search services - Gathers data from Provider A and B in parallel")
public class FlightSearchController {

        private final FlightAggregatorService flightAggregatorService;
        private final CheapestFlightService cheapestFlightService;
        private final ApiLogService apiLogService;

        @GetMapping("/search")
        @Operation(summary = "Search all flights", description = "Searches for all flights from both providers with optional filters.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Flights retrieved successfully", content = @Content(schema = @Schema(implementation = FlightSearchResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<FlightSearchResponse> searchAllFlights(

                        @Parameter(description = "Origin IATA code", example = "IST", required = true) @RequestParam @NotBlank String origin,
                        @Parameter(description = "Destination IATA code", example = "COV", required = true) @RequestParam @NotBlank String destination,
                        @Parameter(description = "Departure date (dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00", required = true) @RequestParam @NotNull @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDate,
                        @Parameter(description = "Minimum price (inclusive)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMin,
                        @Parameter(description = "Maximum price (inclusive)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMax,
                        @Parameter(description = "Departure time lower bound (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateFrom,
                        @Parameter(description = "Departure time upper bound (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateTo,
                        @Parameter(description = "Arrival time lower bound (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateFrom,
                        @Parameter(description = "Arrival time upper bound (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateTo

        ) {
                long startTime = System.currentTimeMillis();

                FlightSearchRequest request = FlightSearchRequest.builder()
                                .origin(origin)
                                .destination(destination)
                                .departureDate(departureDate)
                                .priceMin(priceMin)
                                .priceMax(priceMax)
                                .departureDateFrom(departureDateFrom)
                                .departureDateTo(departureDateTo)
                                .arrivalDateFrom(arrivalDateFrom)
                                .arrivalDateTo(arrivalDateTo)
                                .build();

                log.info("[/search] {} → {} @ {} | filters: {}", origin, destination, departureDate,
                                request.hasActiveFilters() ? "active" : "none");

                List<FlightDto> flights = flightAggregatorService.searchAllFlights(request);
                FlightSearchResponse response = FlightSearchResponse.of(flights);

                long durationMs = System.currentTimeMillis() - startTime;
                apiLogService.logApiCall("/api/v1/flights/search", request, response, 200, durationMs);

                log.info("[/search] {} flights returned in {}ms", flights.size(), durationMs);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/search/cheapest")
        @Operation(summary = "Search cheapest grouped flights", description = "Groups flights by properties and selects the cheapest per group.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Cheapest flights retrieved successfully", content = @Content(schema = @Schema(implementation = FlightSearchResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<FlightSearchResponse> searchCheapestFlights(

                        @Parameter(description = "Origin IATA code", example = "IST", required = true) @RequestParam @NotBlank String origin,
                        @Parameter(description = "Destination IATA code", example = "COV", required = true) @RequestParam @NotBlank String destination,
                        @Parameter(description = "Departure date (dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00", required = true) @RequestParam @NotNull @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDate,
                        @Parameter(description = "Minimum price (inclusive)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMin,
                        @Parameter(description = "Maximum price (inclusive)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMax,
                        @Parameter(description = "Departure time lower bound (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateFrom,
                        @Parameter(description = "Departure time upper bound (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateTo,
                        @Parameter(description = "Arrival time lower bound (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateFrom,
                        @Parameter(description = "Arrival time upper bound (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateTo

        ) {
                long startTime = System.currentTimeMillis();

                FlightSearchRequest request = FlightSearchRequest.builder()
                                .origin(origin)
                                .destination(destination)
                                .departureDate(departureDate)
                                .priceMin(priceMin)
                                .priceMax(priceMax)
                                .departureDateFrom(departureDateFrom)
                                .departureDateTo(departureDateTo)
                                .arrivalDateFrom(arrivalDateFrom)
                                .arrivalDateTo(arrivalDateTo)
                                .build();

                log.info("[/search/cheapest] {} → {} @ {} | filters: {}", origin, destination, departureDate,
                                request.hasActiveFilters() ? "active" : "none");

                List<FlightDto> flights = cheapestFlightService.findCheapestFlights(request);
                FlightSearchResponse response = FlightSearchResponse.of(flights);

                long durationMs = System.currentTimeMillis() - startTime;
                apiLogService.logApiCall("/api/v1/flights/search/cheapest", request, response, 200, durationMs);

                log.info("[/search/cheapest] {} flights returned in {}ms", flights.size(), durationMs);
                return ResponseEntity.ok(response);
        }
}
