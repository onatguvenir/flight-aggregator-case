package com.technoly.domain.port;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;

import java.util.List;

/**
 * Port that orchestrates flight searches across all providers.
 *
 * In the Hexagonal Architecture / Ports & Adapters approach, this interface
 * allows the application layer to search for flights without depending on
 * "external world" details (SOAP, HTTP, vendor DTOs).
 *
 * Expected behavior:
 * - Results coming from providers should be normalized to the common model
 * (FlightDto) we use in the domain.
 * - When a single provider fails, the entire search flow should not crash;
 * if possible, other provider results are returned with "partial success".
 * - Prefer returning an empty list instead of null in case of error/no result
 * (null-safety).
 */
public interface FlightSearchPort {
    List<FlightDto> searchAllFlights(FlightSearchRequest request);
}
