package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API'den dönen uçuş arama yanıtı modeli.
 *
 * Hem "tüm uçuşlar" hem de "en ucuz gruplanmış" servisleri bu response'u
 * kullanır.
 * totalCount, istemcinin sayfalama veya istatistik için kullanabileceği meta
 * bilgidir.
 *
 * Static factory methods ile tutarlı response oluşturma:
 * - FlightSearchResponse.of(flights): normal response
 * - FlightSearchResponse.empty(): hata veya sonuç yok durumu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchResponse {

    /** Dönen uçuş listesi */
    @Builder.Default
    private List<FlightDto> flights = new ArrayList<>();

    /** Toplam uçuş sayısı (UI pagination için) */
    private int totalCount;

    /** Aramanın yapıldığı zaman damgası */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime searchedAt;

    // ---- Static Factory Methods ----

    /**
     * Başarılı yanıt: uçuş listesiyle birlikte
     */
    public static FlightSearchResponse of(List<FlightDto> flights) {
        return FlightSearchResponse.builder()
                .flights(flights)
                .totalCount(flights.size())
                .searchedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Boş yanıt: sağlayıcılardan sonuç gelmedi veya tümü hata verdi
     */
    public static FlightSearchResponse empty() {
        return FlightSearchResponse.builder()
                .flights(new ArrayList<>())
                .totalCount(0)
                .searchedAt(LocalDateTime.now())
                .build();
    }
}
