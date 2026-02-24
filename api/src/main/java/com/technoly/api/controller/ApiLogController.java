package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * REST Controller for API Logs
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "API Logs", description = "Query asynchronously saved API logs with pagination support.")
public class ApiLogController {

        private final ApiLogService apiLogService;

        @GetMapping
        @Operation(summary = "Paginated API Logs", description = "Returns pageable API logs with optional endpoint filtering and sorting.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Logs retrieved successfully", content = @Content(schema = @Schema(implementation = ApiLogEntity.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<Page<ApiLogEntity>> getLogs(
                        @Parameter(description = "Endpoint to filter by (e.g. /api/v1/flights/search)") @RequestParam(required = false) String endpoint,
                        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
                        @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "desc") String sortDir) {

                log.info("API logs request. endpoint={}, page={}, size={}, sort={} {}",
                                endpoint, page, size, sortBy, sortDir);

                Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                                ? Sort.Direction.ASC
                                : Sort.Direction.DESC;

                PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

                Page<ApiLogEntity> logs;
                if (endpoint != null && !endpoint.isBlank()) {
                        logs = apiLogService.getLogsByEndpoint(endpoint, pageable);
                } else {
                        logs = apiLogService.getAllLogs(pageable);
                }

                log.info("Returned page {}/{} from {} records.", page, logs.getTotalPages(), logs.getTotalElements());

                return ResponseEntity.ok(logs);
        }
}
