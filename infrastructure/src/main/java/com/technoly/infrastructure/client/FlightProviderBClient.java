package com.technoly.infrastructure.client;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.util.List;

/**
 * Provider B SOAP Client.
 *
 * Follows the same architectural approach as Provider A:
 * - Common SOAP call/mapping logic in {@link AbstractClient}.
 * - Resilience (retry/circuit breaker/bulkhead) via Resilience4j annotations.
 *
 * Note: Provider names are important for separation in metric labels and logs.
 */
@Component
class FlightProviderBClient extends AbstractClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_B";

    public FlightProviderBClient(
            @Qualifier("webServiceTemplateB") WebServiceTemplate webServiceTemplate,
            MeterRegistry meterRegistry) {
        super(webServiceTemplate, meterRegistry);
    }

    @Override
    @CircuitBreaker(name = "providerB", fallbackMethod = "fallback")
    @Retry(name = "providerB")
    @Bulkhead(name = "providerB", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallback")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        return performSearch(request, PROVIDER_NAME);
    }

    public List<FlightDto> fallback(FlightSearchRequest request, Throwable throwable) {
        return fallbackSearchFlights(request, throwable, PROVIDER_NAME);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
