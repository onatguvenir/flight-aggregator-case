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
 * Provider A ile aynı mimari yaklaşımı izler:
 * - Ortak SOAP çağrı/mapping mantığı {@link AbstractClient} içinde.
 * - Dayanıklılık (retry/circuit breaker/bulkhead) Resilience4j annotation'ları ile.
 *
 * Not: Provider isimleri metrik label'larında ve loglarda ayrıştırma için önemlidir.
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
