package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * REST API'ye gelen uçuş arama isteği modeli.
 *
 * Bean Validation (@NotBlank, @NotNull, @Future) ile input doğrulama.
 *
 * --- Zorunlu Alanlar ---
 * origin, destination, departureDate: Her iki endpoint için zorunlu.
 *
 * --- Opsiyonel Filtre Alanları (Seçenek A: in-memory) ---
 * Bu alanlar FlightFilterService tarafından SOAP çağrısı sonrasında
 * Stream + Predicate zinciri ile uygulanır. Provider'lara iletilmez.
 *
 * Tasarım Kararı (null = filtre yok):
 * - priceMin = null → fiyat alt sınırı kontrolü yapılmaz
 * - departureDateFrom = null → kalkış tarihi alt sınırı kontrolü yapılmaz
 * - vb.
 *
 * Bu "opt-in filter" yaklaşımı, geriye dönük uyumluluk sağlar:
 * Mevcut client'lar filtre parametresi göndermeden çalışmaya devam eder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchRequest {

    // ===========================
    // Zorunlu Arama Parametreleri
    // ===========================

    /** Kalkış havaalanı IATA kodu (örn: IST) */
    @NotBlank(message = "Kalkış yeri (origin) boş olamaz")
    private String origin;

    /** Varış havaalanı IATA kodu (örn: COV) */
    @NotBlank(message = "Varış yeri (destination) boş olamaz")
    private String destination;

    /** Kalkış tarihi: gelecekte bir tarih olmalı */
    @NotNull(message = "Kalkış tarihi (departureDate) null olamaz")
    @Future(message = "Kalkış tarihi gelecekte bir tarih olmalıdır")
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDate;

    // ===========================
    // Opsiyonel Fiyat Filtreleri
    // ===========================

    /**
     * Minimum fiyat filtresi (dahil).
     * null ise alt sınır uygulanmaz.
     * 
     * @PositiveOrZero: 0 veya pozitif ondalıklı sayı beklenir.
     */
    @PositiveOrZero(message = "Minimum fiyat 0 veya pozitif olmalıdır")
    private BigDecimal priceMin;

    /**
     * Maksimum fiyat filtresi (dahil).
     * null ise üst sınır uygulanmaz.
     */
    @PositiveOrZero(message = "Maksimum fiyat 0 veya pozitif olmalıdır")
    private BigDecimal priceMax;

    // ===========================
    // Opsiyonel Tarih Filtreleri
    // ===========================

    /**
     * Kalkış zamanı alt sınırı (inclusive: "from").
     * Bu değerden önce kalkan uçuşlar filtrelenir.
     * null ise alt sınır uygulanmaz.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateFrom;

    /**
     * Kalkış zamanı üst sınırı (inclusive: "to").
     * Bu değerden sonra kalkan uçuşlar filtrelenir.
     * null ise üst sınır uygulanmaz.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateTo;

    /**
     * Varış zamanı alt sınırı (inclusive: "from").
     * Bu değerden önce gelen uçuşlar filtrelenir.
     * null ise alt sınır uygulanmaz.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateFrom;

    /**
     * Varış zamanı üst sınırı (inclusive: "to").
     * Bu değerden sonra gelen uçuşlar filtrelenir.
     * null ise üst sınır uygulanmaz.
     */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateTo;

    /**
     * Bu request'in herhangi bir aktif filtresi olup olmadığını döner.
     * Cache key üretiminde ve log mesajlarında kullanışlıdır.
     *
     * Pure function: dış duruma bağımlı değil, aynı input → aynı output.
     */
    public boolean hasActiveFilters() {
        return priceMin != null || priceMax != null
                || departureDateFrom != null || departureDateTo != null
                || arrivalDateFrom != null || arrivalDateTo != null;
    }
}
