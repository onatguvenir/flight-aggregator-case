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
 * SOAP provider client'ları için ortak davranışları tutan soyut sınıf.
 *
 * Bu sınıfın amacı:
 * - SOAP request oluşturma (domain request → provider request)
 * - SOAP çağrısını yapma (marshalSendAndReceive)
 * - Provider response'unu ortak modele map etme (SearchResult → FlightDto)
 * - Gözlemlenebilirlik (metrikler + loglar)
 *
 * Not:
 * - Resilience4j (retry/circuit breaker/bulkhead) kararları genellikle
 *   somut provider sınıflarında annotation ile tanımlanır.
 * - Bu sınıf, "tek bir yerden" hata/metric/log davranışını standardize eder.
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
        // Defensive Programming: Guard Clauses
        if (request == null) {
            log.warn("[{}] Uçuş arama isteği null olamaz.", providerName);
            return Collections.emptyList();
        }
        if (request.getOrigin() == null || request.getDestination() == null) {
            log.warn("[{}] Uçuş kalkış ve varış noktaları zorunludur.", providerName);
            return Collections.emptyList();
        }

        log.info("[{}] Uçuş araması (SOAP): {} → {} @ {}",
                providerName, request.getOrigin(), request.getDestination(), request.getDepartureDate());

        // Metrik: provider'a yapılan toplam çağrı sayısı
        meterRegistry.counter("flight.search.provider.calls.total", "provider", providerName).increment();

        SearchRequest providerRequest = buildProviderRequest(request);
        Timer.Sample sample = Timer.start(meterRegistry);
        SearchResult result;

        try {
            result = (SearchResult) webServiceTemplate.marshalSendAndReceive(providerRequest);
            // Metrik: başarılı provider çağrıları
            meterRegistry.counter("flight.search.provider.success", "provider", providerName).increment();
        } catch (Exception e) {
            // Metrik: provider hata sayısı
            meterRegistry.counter("flight.search.provider.errors", "provider", providerName).increment();
            log.error("[{}] SOAP iletişim hatası: {}", providerName, e.getMessage());

            // Exception'ı yukarı fırlatmak önemlidir:
            // - Resilience4j annotation'ları bu exception'ı görüp
            //   retry/circuit-breaker/fallback kararlarını uygulayabilir.
            throw e;
        } finally {
            // Metrik: provider çağrısı süreleri (latency)
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

        // Functional approach: Optional ile if-not-null kontrolü
        Optional.ofNullable(request.getDepartureDate())
                .ifPresent(date -> req.setDepartureDate(date.format(DATE_FORMATTER)));

        return req;
    }

    private List<FlightDto> mapToFlightDtos(SearchResult result, String providerName) {
        return Optional.ofNullable(result)
                .filter(res -> {
                    if (res.isHasError()) {
                        log.warn("[{}] Provider hata döndürdü: {}", providerName, res.getErrorMessage());
                        return false;
                    }
                    return true;
                })
                .map(SearchResult::getFlights)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(java.util.Objects::nonNull) // Defensive: Liste içindeki olası null elemanları eledik
                .map(flight -> FlightDto.builder()
                        .flightNumber(Optional.ofNullable(flight.getFlightNumber()).orElse("UNKNOWN"))
                        .origin(Optional.ofNullable(flight.getOrigin()).orElse("UNKNOWN"))
                        .destination(Optional.ofNullable(flight.getDestination()).orElse("UNKNOWN"))
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
