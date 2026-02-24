package com.technoly.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import com.technoly.infrastructure.persistence.repository.ApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API Log Service
 *
 * Asynchronously saves every REST API request and response to PostgreSQL
 * and provides paginated listing operations.
 *
 * Why is Pagination Mandatory?
 * The api_logs table can contain millions of records in production.
 * findAll() → pulling all rows into memory → OOM (Out of Memory) error.
 * findAll(Pageable) → brings only the requested page → saves memory.
 *
 * With @Async, this job is done in a background thread, not waiting for user
 * response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogService {

    private final ApiLogRepository apiLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Logs the API call asynchronously to the database.
     *
     * @Async: This method runs in its own thread, does not block the caller.
     *         → @EnableAsync annotation is required
     *         (in FlightAggregatorApplication).
     *
     * @Transactional: Independent transaction with REQUIRES_NEW.
     *                 → Log is written even if the caller fails.
     *
     * @param endpoint   Called endpoint: "/api/v1/flights/search"
     * @param request    Request object (to be converted to JSON)
     * @param response   Response object (to be converted to JSON), can be null
     * @param statusCode HTTP status code
     * @param durationMs Duration of operation (milliseconds)
     */
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logApiCall(String endpoint, Object request, Object response,
            Integer statusCode, Long durationMs) {
        try {
            String requestJson = toJson(request);
            String responseJson = toJson(response);

            ApiLogEntity logEntity = ApiLogEntity.builder()
                    .endpoint(endpoint)
                    .request(requestJson)
                    .response(responseJson)
                    .statusCode(statusCode)
                    .durationMs(durationMs)
                    .build();

            apiLogRepository.save(logEntity);

            log.debug("API log saved: endpoint={}, status={}, duration={}ms",
                    endpoint, statusCode, durationMs);

        } catch (Exception e) {
            // Log operation MUST NEVER throw an exception (fire-and-forget)
            log.error("Failed to save API log: {}", e.getMessage(), e);
        }
    }

    // =====================================================================
    // Paginated Query Methods (Production-Safe)
    // =====================================================================

    /**
     * Lists all logs with pagination.
     *
     * Example usage: Pageable.of(0, 20, Sort.by("createdAt").descending())
     *
     * @param pageable Page number, size, and sorting information
     * @return Paginated log objects
     */
    public Page<ApiLogEntity> getAllLogs(Pageable pageable) {
        return apiLogRepository.findAll(pageable);
    }

    /**
     * Lists paginated logs for a specific endpoint.
     *
     * @param endpoint Endpoint path to search (e.g., /api/v1/flights/search)
     * @param pageable Page number, size, and sorting information
     * @return Paginated log objects
     */
    public Page<ApiLogEntity> getLogsByEndpoint(String endpoint, Pageable pageable) {
        return apiLogRepository.findByEndpoint(endpoint, pageable);
    }

    // =====================================================================
    // Legacy (Backward Compatible) — All data without pagination
    // =====================================================================

    /**
     * Lists all logs (without pagination).
     * 
     * @deprecated Causes OOM risks in production; use getAllLogs(Pageable)
     *             instead.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public List<ApiLogEntity> getAllLogs() {
        return apiLogRepository.findAll();
    }

    /**
     * Lists all logs for a specific endpoint (without pagination).
     * 
     * @deprecated Causes OOM risks in production; use getLogsByEndpoint(String,
     *             Pageable) instead.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public List<ApiLogEntity> getLogsByEndpoint(String endpoint) {
        return apiLogRepository.findByEndpoint(endpoint);
    }

    /**
     * Converts the object to JSON string.
     * Null-safe: null input → returns "null" string.
     */
    private String toJson(Object obj) {
        if (obj == null)
            return "null";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization error: {}", e.getMessage());
            return "\"serialization_error\"";
        }
    }
}
