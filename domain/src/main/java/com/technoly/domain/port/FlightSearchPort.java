package com.technoly.domain.port;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;

import java.util.List;

/**
 * Port for orchestrating flight searches across all available providers.
 */
public interface FlightSearchPort {
    List<FlightDto> searchAllFlights(FlightSearchRequest request);
}
