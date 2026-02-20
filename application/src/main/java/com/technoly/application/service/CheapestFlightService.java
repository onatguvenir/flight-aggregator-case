package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * En Ucuz Uçuş Servisi - Service 2
 *
 * Akış:
 * 1. FlightAggregatorService'den tüm (ham, filtresiz) uçuşları al
 * 2. Composite key ile grupla (flightNumber, origin, dest, depDT, arrDT)
 * 3. Her gruptan en ucuzunu seç (minBy price)
 * 4. Fiyat filtresi dahil tüm filtreler uygulanmış listeyi döndür
 *
 * Filtre uygulama sırası (önemli!):
 * Gruplama ÖNCE yapılır (cheapest semantiği korunur),
 * fiyat/tarih filtreleri SONRA uygulanır.
 *
 * Neden ayrı @Cacheable?
 * "cheapestFlights" cache, aynı keyle "flightSearch" cache'den farklı
 * sonuç döner (gruplandırılmış vs tümü). Ayrı namespace zorunludur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheapestFlightService {

        private final FlightAggregatorService flightAggregatorService;
        private final FlightFilterService flightFilterService;

        @Cacheable(value = "cheapestFlights", key = "#request.origin + '_' + #request.destination + '_' + #request.departureDate"
                        + " + '_' + #request.priceMin + '_' + #request.priceMax"
                        + " + '_' + #request.departureDateFrom + '_' + #request.departureDateTo"
                        + " + '_' + #request.arrivalDateFrom + '_' + #request.arrivalDateTo", unless = "#result.isEmpty()")
        public List<FlightDto> findCheapestFlights(FlightSearchRequest request) {
                log.info("En ucuz uçuş araması: {} → {} @ {} [filtreler: {}]",
                                request.getOrigin(), request.getDestination(), request.getDepartureDate(),
                                request.hasActiveFilters() ? "aktif" : "yok");

                // Adım 1: Tüm ham uçuşları al (FlightAggregatorService kendi cache + filter'ını
                // uygular)
                List<FlightDto> allFlights = flightAggregatorService.searchAllFlights(request);

                if (allFlights.isEmpty()) {
                        return List.of();
                }

                // Adım 2 & 3: Grupla ve her gruptan en ucuzunu seç
                // nullsLast: fiyatsız uçuşlar gruplama/sıralamada en sona düşer
                Comparator<BigDecimal> nullSafePrice = Comparator.nullsLast(Comparator.naturalOrder());

                List<FlightDto> cheapestFlights = allFlights.stream()
                                .collect(Collectors.groupingBy(
                                                FlightGroupKey::of,
                                                Collectors.minBy(Comparator.comparing(
                                                                FlightDto::getPrice,
                                                                nullSafePrice))))
                                .values()
                                .stream()
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .sorted(Comparator.comparing(FlightDto::getPrice, nullSafePrice))
                                .collect(Collectors.toList());

                log.info("Gruplama: {}→{} uçuş (filtrelenmiş ham listeden)",
                                allFlights.size(), cheapestFlights.size());

                return cheapestFlights;
        }

        /**
         * Composite Gruplama Anahtarı (Java Record)
         *
         * Immutable, equals/hashCode otomatik üretilir.
         * (flightNo + origin + dest + depDT + arrDT) aynı ise aynı grup.
         */
        private record FlightGroupKey(
                        String flightNumber,
                        String origin,
                        String destination,
                        LocalDateTime departureDateTime,
                        LocalDateTime arrivalDateTime) {
                static FlightGroupKey of(FlightDto flight) {
                        return new FlightGroupKey(
                                        flight.getFlightNumber(),
                                        flight.getOrigin(),
                                        flight.getDestination(),
                                        flight.getDepartureDateTime(),
                                        flight.getArrivalDateTime());
                }
        }
}
