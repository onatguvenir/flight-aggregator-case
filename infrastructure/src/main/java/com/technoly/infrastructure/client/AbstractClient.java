package com.technoly.infrastructure.client;

import com.flightprovider.wsdl.SearchRequest;
import com.flightprovider.wsdl.SearchResult;
import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract client containing common SOAP communication and mapping logic for
 * Flight Providers.
 */
@Slf4j
abstract class AbstractClient {

    // İstenen tarih formatına (Örn. "01-06-2026T15:00") göre tarih nesnelerini
    // metne çevirmek (serialize) veya metinden nesneye dönüştürmek (deserialize)
    // için kullandığımız Formatter nesnesi.
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm");

    private final WebServiceTemplate webServiceTemplate;
    private final MeterRegistry meterRegistry;

    protected AbstractClient(WebServiceTemplate webServiceTemplate, MeterRegistry meterRegistry) {
        this.webServiceTemplate = webServiceTemplate;
        this.meterRegistry = meterRegistry;
    }

    protected List<FlightDto> performSearch(FlightSearchRequest request, String providerName) {
        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            log.warn("[{}] Invalid search request.", providerName);
            return Collections.emptyList();
        }

        log.info("[{}] Uçuş araması (SOAP): {} → {} @ {}",
                providerName, request.getOrigin(), request.getDestination(), request.getDepartureDate());

        SearchRequest providerRequest = buildProviderRequest(request);
        Timer.Sample sample = Timer.start(meterRegistry);
        SearchResult result;

        try {
            result = (SearchResult) webServiceTemplate.marshalSendAndReceive(providerRequest);
        } catch (Exception e) {
            log.error("[{}] SOAP iletişim hatası: {}", providerName, e.getMessage());
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("flight.search.provider.latency", "provider", providerName));
        }

        return mapToFlightDtos(result, providerName);
    }

    protected List<FlightDto> fallbackSearchFlights(FlightSearchRequest request, Throwable throwable,
            String providerName) {
        log.warn("[{}] Fallback devrede. Origin: {}, Hata: {}", providerName,
                Optional.ofNullable(request).map(FlightSearchRequest::getOrigin).orElse("Unknown"),
                throwable.getMessage());
        return Collections.emptyList();
    }

    private SearchRequest buildProviderRequest(FlightSearchRequest request) {
        SearchRequest req = new SearchRequest();
        req.setOrigin(request.getOrigin());
        req.setDestination(request.getDestination());
        if (request.getDepartureDate() != null) {
            // İstemciden (Controller üzerinden) gelen LocalDateTime tipindeki uçuş
            // tarihini,
            // SOAP Web Servisinin beklediği "dd-MM-yyyy'T'HH:mm" yapısındaki String formata
            // dönüştürüyoruz.
            req.setDepartureDate(request.getDepartureDate().format(DATE_FORMATTER));
        }
        return req;
    }

    private List<FlightDto> mapToFlightDtos(SearchResult result, String providerName) {
        if (result == null) {
            log.warn("[{}] SearchResult null döndü", providerName);
            return Collections.emptyList();
        }

        if (result.isHasError()) {
            log.warn("[{}] Provider hata döndürdü: {}", providerName, result.getErrorMessage());
            return Collections.emptyList();
        }

        return Optional.ofNullable(result.getFlights())
                .orElseGet(Collections::emptyList)
                .stream()
                .map(flight -> FlightDto.builder()
                        .flightNumber(flight.getFlightNumber())
                        .origin(flight.getOrigin())
                        .destination(flight.getDestination())
                        .departureDateTime(parseDateTime(flight.getDepartureTime()))
                        .arrivalDateTime(parseDateTime(flight.getArrivalTime()))
                        .price(Optional.ofNullable(flight.getPrice()).orElse(BigDecimal.ZERO))
                        .provider(providerName)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Uçak sağlayıcıdan (SOAP Servisinden) gelen String formatındaki tarih
     * bilgisini,
     * uygulamamızın içinde evrensel olarak kullanabilmek için Java'nın
     * LocalDateTime nesnesine geri dönüştürür.
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            // Eğer dış sağlayıcı hatalı veya boş bir tarih dönerse, sistemin çökmesini
            // engellemek için
            // geçici veya yedek (fallback) bir değer olarak anlık tarihi döner.
            return LocalDateTime.now(); // Fallback if provider returns bad date
        }
        // Gelen String tarihi, belirlediğimiz "dd-MM-yyyy'T'HH:mm" format kuralına göre
        // ayrıştırarak parse ederiz.
        return LocalDateTime.parse(dateTimeStr, DATE_FORMATTER);
    }
}
