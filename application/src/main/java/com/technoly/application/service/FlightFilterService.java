package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Uçuş Filtre Servisi
 *
 * FlightSearchRequest'teki opsiyonel filtre parametrelerini
 * (priceMin, priceMax, departureDateFrom, departureDateTo,
 * arrivalDateFrom, arrivalDateTo) uçuş listesine uygular.
 *
 * ---- Tasarım İlkeleri ----
 *
 * 1. Pure Function (Fonksiyonel Yaklaşım):
 * applyFilters() dış durumu değiştirmez, new list döner.
 * Input list'i mutate etmez. → Thread-safe, test edilmesi kolay.
 *
 * 2. Composable Predicate Chain (Strategy Pattern):
 * Her filtre bağımsız bir Predicate<FlightDto>'dur.
 * Predicate.and() ile zincir kurulur; null predicate'ler atlanır.
 * Yeni filtre eklenmek istendiğinde sadece yeni Predicate eklenir (OCP).
 *
 * 3. Null = "Filtre yok" Semantiği:
 * Parametre null ise Predicate.alwaysTrue() döner (guard clause).
 * Bu sayede her parametre bağımsız olarak opt-in edilebilir.
 *
 * 4. Defensive (Null-safe) Karşılaştırma:
 * FlightDto alanları null olabilir (sağlayıcıdan gelmemiş).
 * Null alan varsa o predicate false döner (uçuş filtrelenir).
 *
 * Örnek:
 * applyFilters(flights, req.priceMin=200, req.priceMax=400)
 * → sadece 200 ≤ price ≤ 400 olan uçuşlar döner
 */
@Slf4j
@Service
public class FlightFilterService {

    /**
     * Tüm aktif filtreleri uçuş listesine uygular.
     *
     * @param flights Filtrelenecek uçuş listesi
     * @param request Filtre parametrerini içeren arama isteği
     * @return Filtrelenmiş yeni liste (orijinal liste değişmez)
     */
    public List<FlightDto> applyFilters(List<FlightDto> flights, FlightSearchRequest request) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }

        if (!request.hasActiveFilters()) {
            // Hiç filtre yoksa Stream overhead'inden kaçın
            return flights;
        }

        // Composable Predicate zinciri oluştur
        // Her predicate null-safe guard clause ile korunur
        Predicate<FlightDto> combinedFilter = buildPricePredicate(request)
                .and(buildDepartureDateFromPredicate(request))
                .and(buildDepartureDateToPredicate(request))
                .and(buildArrivalDateFromPredicate(request))
                .and(buildArrivalDateToPredicate(request));

        List<FlightDto> filtered = flights.stream()
                .filter(combinedFilter)
                .collect(Collectors.toList());

        log.debug("Filtre uygulandı: {} → {} uçuş (filtreler: {})",
                flights.size(), filtered.size(), describeFilters(request));

        return filtered;
    }

    // ---- Private Predicate Builders ----

    /**
     * Fiyat aralığı predicate'i (priceMin ve priceMax birlikte ele alınır).
     * priceMin null → alt sınır yok; priceMax null → üst sınır yok.
     * Flight.price null → uçuş filtrelenir (defensif).
     */
    private Predicate<FlightDto> buildPricePredicate(FlightSearchRequest req) {
        return flight -> {
            if (flight.getPrice() == null) {
                // Fiyatı bilinmeyen uçuş, fiyat filtresi varsa elenmir
                return req.getPriceMin() == null && req.getPriceMax() == null;
            }
            boolean minOk = req.getPriceMin() == null
                    || flight.getPrice().compareTo(req.getPriceMin()) >= 0;
            boolean maxOk = req.getPriceMax() == null
                    || flight.getPrice().compareTo(req.getPriceMax()) <= 0;
            return minOk && maxOk;
        };
    }

    /**
     * Kalkış zamanı alt sınırı: departureDateTime >= departureDateFrom
     */
    private Predicate<FlightDto> buildDepartureDateFromPredicate(FlightSearchRequest req) {
        if (req.getDepartureDateFrom() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getDepartureDateTime() != null
                && !flight.getDepartureDateTime().isBefore(req.getDepartureDateFrom());
    }

    /**
     * Kalkış zamanı üst sınırı: departureDateTime <= departureDateTo
     */
    private Predicate<FlightDto> buildDepartureDateToPredicate(FlightSearchRequest req) {
        if (req.getDepartureDateTo() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getDepartureDateTime() != null
                && !flight.getDepartureDateTime().isAfter(req.getDepartureDateTo());
    }

    /**
     * Varış zamanı alt sınırı: arrivalDateTime >= arrivalDateFrom
     */
    private Predicate<FlightDto> buildArrivalDateFromPredicate(FlightSearchRequest req) {
        if (req.getArrivalDateFrom() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getArrivalDateTime() != null
                && !flight.getArrivalDateTime().isBefore(req.getArrivalDateFrom());
    }

    /**
     * Varış zamanı üst sınırı: arrivalDateTime <= arrivalDateTo
     */
    private Predicate<FlightDto> buildArrivalDateToPredicate(FlightSearchRequest req) {
        if (req.getArrivalDateTo() == null)
            return Predicate.not(Objects::isNull);
        return flight -> flight.getArrivalDateTime() != null
                && !flight.getArrivalDateTime().isAfter(req.getArrivalDateTo());
    }

    /** Log açıklaması için aktif filtreleri özetle */
    private String describeFilters(FlightSearchRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getPriceMin() != null)
            sb.append("priceMin=").append(req.getPriceMin()).append(" ");
        if (req.getPriceMax() != null)
            sb.append("priceMax=").append(req.getPriceMax()).append(" ");
        if (req.getDepartureDateFrom() != null)
            sb.append("depFrom=").append(req.getDepartureDateFrom()).append(" ");
        if (req.getDepartureDateTo() != null)
            sb.append("depTo=").append(req.getDepartureDateTo()).append(" ");
        if (req.getArrivalDateFrom() != null)
            sb.append("arrFrom=").append(req.getArrivalDateFrom()).append(" ");
        if (req.getArrivalDateTo() != null)
            sb.append("arrTo=").append(req.getArrivalDateTo()).append(" ");
        return sb.toString().trim();
    }
}
