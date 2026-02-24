package com.technoly.domain.port;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;

import java.util.List;

/**
 * Flight Provider Port Interface (Hexagonal Architecture / Port & Adapter)
 *
 * This interface ensures that the application layer is not dependent on
 * the infrastructure layer. Strategy Pattern is implemented:
 * - FlightProviderAClient -> FlightProviderPort implements
 * - FlightProviderBClient -> FlightProviderPort implements
 *
 * Application service can work with both implementations via
 * List<FlightProviderPort>. When a new provider is added, only a new
 * class implementing this interface is written (OCP - Open/Closed
 * Principle).
 *
 * Dependency Inversion Principle (DIP):
 * - High level (application) → depends on abstraction (port)
 * - Low level (infrastructure) → implements abstraction
 */
public interface FlightProviderPort {

    /**
     * Performs a flight availability search from the provider.
     *
     * @param request Search parameters (origin, destination, departureDate)
     * @return Normalized FlightDto list returned from the provider.
     *         Returns an empty list (not null) in case of error or empty result.
     */
    List<FlightDto> searchFlights(FlightSearchRequest request);

    /**
     * Returns the name of this provider (for logging and metrics).
     * Example: "PROVIDER_A", "PROVIDER_B"
     */
    String getProviderName();
}
