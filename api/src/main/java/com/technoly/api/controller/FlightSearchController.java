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
 * Uçuş Arama REST Controller
 *
 * Endpoint'ler:
 * 1. GET /api/v1/flights/search → Tüm uçuşlar + opsiyonel filtreler
 * 2. GET /api/v1/flights/search/cheapest → Gruplanmış en ucuz + opsiyonel
 * filtreler
 *
 * Opsiyonel Filtreler (her iki endpoint için):
 * - priceMin / priceMax : Fiyat aralığı
 * - departureDateFrom/To : Kalkış zaman aralığı (between)
 * - arrivalDateFrom/To : Varış zaman aralığı (between)
 *
 * Filtreler null ise uygulanmaz (opt-in semantiği).
 * Filtreler in-memory uygulanır, SOAP provider'a iletilmez.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Tag(name = "Flight Search", description = "Uçuş arama servisleri - Provider A ve B'den paralel veri toplar")
public class FlightSearchController {

        private final FlightAggregatorService flightAggregatorService;
        private final CheapestFlightService cheapestFlightService;
        private final ApiLogService apiLogService;

        /**
         * Service 1: Tüm uçuşları ara (birleştirilmiş + filtrelenmiş)
         */
        @GetMapping("/search")
        @Operation(summary = "Tüm uçuşları ara", description = """
                        FlightProviderA ve FlightProviderB'den gelen tüm uçuşları paralel toplar.
                        Opsiyonel filtreler: fiyat aralığı (priceMin/priceMax),
                        kalkış zaman aralığı (departureDateFrom/To),
                        varış zaman aralığı (arrivalDateFrom/To).
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Uçuşlar başarıyla listelendi", content = @Content(schema = @Schema(implementation = FlightSearchResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Geçersiz istek parametreleri"),
                        @ApiResponse(responseCode = "500", description = "Sunucu hatası")
        })
        public ResponseEntity<FlightSearchResponse> searchAllFlights(

                        // ---- Zorunlu Parametreler ----
                        @Parameter(description = "Kalkış IATA kodu", example = "IST", required = true) @RequestParam @NotBlank String origin,

                        @Parameter(description = "Varış IATA kodu", example = "COV", required = true) @RequestParam @NotBlank String destination,

                        @Parameter(description = "Kalkış tarihi (dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00", required = true) @RequestParam @NotNull @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDate,

                        // ---- Opsiyonel Fiyat Filtreleri ----
                        @Parameter(description = "Minimum fiyat (dahil, örn: 100.00)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMin,

                        @Parameter(description = "Maksimum fiyat (dahil, örn: 500.00)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMax,

                        // ---- Opsiyonel Kalkış Tarihi Filtreleri ----
                        @Parameter(description = "Kalkış zamanı alt sınırı (from, dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateFrom,

                        @Parameter(description = "Kalkış zamanı üst sınırı (to, dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateTo,

                        // ---- Opsiyonel Varış Tarihi Filtreleri ----
                        @Parameter(description = "Varış zamanı alt sınırı (from, dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateFrom,

                        @Parameter(description = "Varış zamanı üst sınırı (to, dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateTo

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

                log.info("[/search] {} → {} @ {} | filtreler: {}", origin, destination, departureDate,
                                request.hasActiveFilters() ? "aktif" : "yok");

                List<FlightDto> flights = flightAggregatorService.searchAllFlights(request);
                FlightSearchResponse response = FlightSearchResponse.of(flights);

                long durationMs = System.currentTimeMillis() - startTime;
                apiLogService.logApiCall("/api/v1/flights/search", request, response, 200, durationMs);

                log.info("[/search] {} uçuş döndü, {}ms", flights.size(), durationMs);
                return ResponseEntity.ok(response);
        }

        /**
         * Service 2: Gruplanmış en ucuz uçuşları ara (filtrelenmiş)
         */
        @GetMapping("/search/cheapest")
        @Operation(summary = "En ucuz gruplanmış uçuşları ara", description = """
                        Uçuşları (flightNumber, origin, destination, departureDateTime, arrivalDateTime)
                        anahtarına göre gruplar ve her gruptan en ucuzunu seçer.
                        Opsiyonel filtreler aynı şekilde uygulanır.
                        NOT: Filtreler gruplama öncesinde uygulanır; bu cheapest semantiğini korur.
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "En ucuz uçuşlar listelendi", content = @Content(schema = @Schema(implementation = FlightSearchResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Geçersiz istek parametreleri"),
                        @ApiResponse(responseCode = "500", description = "Sunucu hatası")
        })
        public ResponseEntity<FlightSearchResponse> searchCheapestFlights(

                        @Parameter(description = "Kalkış IATA kodu", example = "IST", required = true) @RequestParam @NotBlank String origin,

                        @Parameter(description = "Varış IATA kodu", example = "COV", required = true) @RequestParam @NotBlank String destination,

                        @Parameter(description = "Kalkış tarihi (dd-MM-yyyy'T'HH:mm)", example = "01-06-2026T00:00", required = true) @RequestParam @NotNull @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDate,

                        @Parameter(description = "Minimum fiyat (dahil, örn: 100.00)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMin,

                        @Parameter(description = "Maksimum fiyat (dahil, örn: 500.00)") @RequestParam(required = false) @PositiveOrZero BigDecimal priceMax,

                        @Parameter(description = "Kalkış zamanı alt sınırı (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateFrom,

                        @Parameter(description = "Kalkış zamanı üst sınırı (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime departureDateTo,

                        @Parameter(description = "Varış zamanı alt sınırı (from)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateFrom,

                        @Parameter(description = "Varış zamanı üst sınırı (to)") @RequestParam(required = false) @DateTimeFormat(pattern = "dd-MM-yyyy'T'HH:mm") LocalDateTime arrivalDateTo

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

                log.info("[/search/cheapest] {} → {} @ {} | filtreler: {}", origin, destination, departureDate,
                                request.hasActiveFilters() ? "aktif" : "yok");

                List<FlightDto> flights = cheapestFlightService.findCheapestFlights(request);
                FlightSearchResponse response = FlightSearchResponse.of(flights);

                long durationMs = System.currentTimeMillis() - startTime;
                apiLogService.logApiCall("/api/v1/flights/search/cheapest", request, response, 200, durationMs);

                log.info("[/search/cheapest] {} uçuş, {}ms", flights.size(), durationMs);
                return ResponseEntity.ok(response);
        }
}
