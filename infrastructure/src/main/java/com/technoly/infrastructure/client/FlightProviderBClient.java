package com.technoly.infrastructure.client;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FlightProvider B - SOAP Client
 *
 * ProviderA ile aynı pattern; fark:
 * - ProviderB'nin SOAP namespace'i farklı
 * - Request: departure/arrival (origin/destination değil)
 * - Response: flightNumber (flightNo değil)
 *
 * Circuit Breaker: name="providerB" → application.yml'deki config
 * Retry: name="providerB" → ayrı retry config (farklı timeout ayarları
 * olabilir)
 *
 * Neden ayrı Circuit Breaker? ProviderA down olduğunda ProviderB'nin
 * Circuit Breaker'ı bozulmamalı. İzolasyon prensibine uygun.
 */
@Slf4j
@Component
public class FlightProviderBClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_B";

    private final WebServiceTemplate webServiceTemplate;

    @Value("${providers.b.url:http://localhost:8082/ws}")
    private String providerBUrl;

    public FlightProviderBClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    /**
     * ProviderB'ye SOAP isteği gönder.
     * Resilience4j @CircuitBreaker + @Retry ile korunmuş.
     */
    @Override
    @CircuitBreaker(name = "providerB", fallbackMethod = "fallbackSearchFlights")
    @Retry(name = "providerB")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        log.info("[ProviderB Client] SOAP isteği gönderiliyor: {} → {} @ {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchRequest soapRequest = buildProviderBRequest(
                request);

        com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchResponse soapResponse = (com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchResponse) webServiceTemplate
                .marshalSendAndReceive(providerBUrl, soapRequest);

        return mapToFlightDtos(soapResponse);
    }

    /**
     * Fallback: Tüm retry'lar başarısız olduktan sonra veya circuit breaker açıkken
     * çağrılır.
     * Null Object Pattern: boş liste döner.
     */
    public List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable) {
        log.warn("[ProviderB Client] Circuit Breaker/Retry exhausted. Fallback devrede. Hata: {}",
                throwable.getMessage());
        return new ArrayList<>();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchRequest buildProviderBRequest(
            FlightSearchRequest request) {
        com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchRequest soapRequest = new com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchRequest();
        // ProviderB: origin → departure, destination → arrival
        soapRequest.setDeparture(request.getOrigin());
        soapRequest.setArrival(request.getDestination());

        try {
            GregorianCalendar cal = GregorianCalendar.from(
                    request.getDepartureDate().atZone(ZoneId.systemDefault()));
            XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
            soapRequest.setDepartureDate(xmlCal);
        } catch (DatatypeConfigurationException e) {
            log.error("[ProviderB Client] Tarih dönüşüm hatası: {}", e.getMessage());
        }

        return soapRequest;
    }

    private List<FlightDto> mapToFlightDtos(
            com.technoly.infrastructure.client.soap.providerb.AvailabilitySearchResponse response) {

        if (response == null) {
            log.warn("[ProviderB Client] SOAP response null döndü");
            return new ArrayList<>();
        }

        if (response.isHasError()) {
            log.warn("[ProviderB Client] Provider hata döndürdü: {}", response.getErrorMessage());
            return new ArrayList<>();
        }

        return Optional.ofNullable(response.getFlightOptions())
                .orElse(new ArrayList<>())
                .stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNumber())
                        .origin(flight.getDeparture()) // normalization: departure → origin
                        .destination(flight.getArrival()) // normalization: arrival → destination
                        .departureDateTime(xmlCalToLocalDateTime(flight.getDeparturedatetime()))
                        .arrivalDateTime(xmlCalToLocalDateTime(flight.getArrivaldatetime()))
                        .price(flight.getPrice() != null ? flight.getPrice() : BigDecimal.ZERO)
                        .provider(PROVIDER_NAME)
                        .build())
                .collect(Collectors.toList());
    }

    private LocalDateTime xmlCalToLocalDateTime(javax.xml.datatype.XMLGregorianCalendar xmlCal) {
        if (xmlCal == null)
            return null;
        return xmlCal.toGregorianCalendar()
                .toZonedDateTime()
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
