package com.technoly.infrastructure.client;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
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
 * FlightProvider A - SOAP Client
 *
 * Bu sınıf, FlightProviderPort interface'ini implement ederek
 * Strategy Pattern'i gerçekleştirir. Application katmanı bu sınıfı
 * doğrudan değil, FlightProviderPort interface'i aracılığıyla kullanır.
 *
 * Resilience4j Annotations (AOP-based):
 * ----------------------------------------
 * 
 * @CircuitBreaker: Provider A down/yavaş ise devre açılır.
 *                  - name="providerA" → application.yml'deki konfigürasyonla
 *                  eşleşir
 *                  - fallbackMethod: devre açıkken çağrılacak metod
 *
 * @Retry: Geçici ağ hatalarında otomatik yeniden deneme.
 *         - name="providerA" → application.yml'deki retry config
 *
 *         NOT: @TimeLimiter sadece CompletableFuture ile çalışır;
 *         senkron metodlar için Resilience4j TimeLimiter programmatic
 *         kullanınız.
 *         Bu proje senkron SOAP kullandığından timeout spring-ws seviyesinde
 *         ayarlanır.
 */
@Slf4j
@Component
public class FlightProviderAClient implements FlightProviderPort {

    private static final String PROVIDER_NAME = "PROVIDER_A";
    private static final String NAMESPACE_URI = "http://www.flightprovidera.com/ws";

    /**
     * WebServiceTemplate: Spring-WS'nin SOAP call için temel client'ı.
     * Neden bean injection değil constructor injection?
     * → SOLID DIP: Spring bağımlılığı Lombok @RequiredArgsConstructor
     * yerine açık constructor ile izole edilir (test edilebilirlik için).
     */
    private final WebServiceTemplate webServiceTemplate;

    @Value("${providers.a.url:http://localhost:8081/ws}")
    private String providerAUrl;

    public FlightProviderAClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    /**
     * ProviderA'ya SOAP isteği gönder.
     *
     * Circuit Breaker durumları:
     * - CLOSED: Normal çalışma, requestler geçer
     * - OPEN: Hata eşiği aşıldı, fallback devreye girer
     * - HALF_OPEN: Test requestleri gönderilir, başarılıysa CLOSED'a döner
     *
     * Retry: maxAttempts=3, exponential backoff ile yeniden dener.
     */
    @Override
    @CircuitBreaker(name = "providerA", fallbackMethod = "fallbackSearchFlights")
    @Retry(name = "providerA")
    public List<FlightDto> searchFlights(FlightSearchRequest request) {
        log.info("[ProviderA Client] SOAP isteği gönderiliyor: {} → {} @ {}",
                request.getOrigin(), request.getDestination(), request.getDepartureDate());

        // Domain request → SOAP request XML nesnesi oluştur
        // Not: JAXB generated sınıflar olmadığı için manuel XML marshallingi yapılır.
        // Gerçek projede jaxb2-maven-plugin ile generate edilmiş sınıflar kullanılır.
        com.technoly.infrastructure.client.soap.providera.AvailabilitySearchRequest soapRequest = buildProviderARequest(
                request);

        com.technoly.infrastructure.client.soap.providera.AvailabilitySearchResponse soapResponse = (com.technoly.infrastructure.client.soap.providera.AvailabilitySearchResponse) webServiceTemplate
                .marshalSendAndReceive(providerAUrl, soapRequest);

        return mapToFlightDtos(soapResponse);
    }

    /**
     * Fallback method: Circuit Breaker açıldığında veya tüm retry'lar başarısız
     * olduğunda çağrılır.
     *
     * Fallback method imzası, orijinal metodla aynı parametrelere sahip olmalı
     * + son parametre olarak Throwable eklenmelidir.
     *
     * Burada boş liste döndürülür (Null Object Pattern):
     * Application katmanı null check yapmak zorunda kalmaz.
     */
    public List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable) {
        log.warn("[ProviderA Client] Circuit Breaker/Retry exhausted. Fallback devrede. Hata: {}",
                throwable.getMessage());
        return new ArrayList<>();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    // ---- Private Helper Methods ----

    private com.technoly.infrastructure.client.soap.providera.AvailabilitySearchRequest buildProviderARequest(
            FlightSearchRequest request) {
        com.technoly.infrastructure.client.soap.providera.AvailabilitySearchRequest soapRequest = new com.technoly.infrastructure.client.soap.providera.AvailabilitySearchRequest();
        soapRequest.setOrigin(request.getOrigin());
        soapRequest.setDestination(request.getDestination());

        try {
            GregorianCalendar cal = GregorianCalendar.from(
                    request.getDepartureDate().atZone(ZoneId.systemDefault()));
            XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
            soapRequest.setDepartureDate(xmlCal);
        } catch (DatatypeConfigurationException e) {
            log.error("[ProviderA Client] Tarih dönüşüm hatası: {}", e.getMessage());
        }

        return soapRequest;
    }

    private List<FlightDto> mapToFlightDtos(
            com.technoly.infrastructure.client.soap.providera.AvailabilitySearchResponse response) {

        if (response == null) {
            log.warn("[ProviderA Client] SOAP response null döndü");
            return new ArrayList<>();
        }

        if (response.isHasError()) {
            log.warn("[ProviderA Client] Provider hata döndürdü: {}", response.getErrorMessage());
            return new ArrayList<>();
        }

        return Optional.ofNullable(response.getFlightOptions())
                .orElse(new ArrayList<>())
                .stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNo())
                        .origin(flight.getOrigin())
                        .destination(flight.getDestination())
                        .departureDateTime(xmlCalToLocalDateTime(flight.getDeparturedatetime()))
                        .arrivalDateTime(xmlCalToLocalDateTime(flight.getArrivaldatetime()))
                        .price(flight.getPrice() != null ? flight.getPrice() : BigDecimal.ZERO)
                        .provider(PROVIDER_NAME)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * XMLGregorianCalendar → LocalDateTime dönüşümü
     * Null-safe: null gelirse null döner (Optional kullanımına gerek yok, defensive
     * check yeterli)
     */
    private LocalDateTime xmlCalToLocalDateTime(javax.xml.datatype.XMLGregorianCalendar xmlCal) {
        if (xmlCal == null)
            return null;
        return xmlCal.toGregorianCalendar()
                .toZonedDateTime()
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
