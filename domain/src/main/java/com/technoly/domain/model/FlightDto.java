package com.technoly.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uçuş Veri Transfer Nesnesi (DTO)
 *
 * Bu sınıf, hem ProviderA hem de ProviderB'den gelen uçuş verilerini
 * normalleştirmek için kullanılır. Her iki sağlayıcının farklı alan
 * adları (origin/destination vs departure/arrival) bu ortak model'de birleşir.
 *
 * @Builder: Fluent API ile nesne oluşturma (null safety için)
 * @Data: @Getter, @Setter, @ToString, @EqualsAndHashCode (Lombok)
 *
 *        Neden DTO? Entity (DB) sınıfları ile aynı sınıfı kullanmak
 *        SRP'yi ihlal eder. DTO sadece veri taşır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightDto {

    /** Uçuş numarası: TK1001, PC1002 vb. — gruplama anahtarının bir parçası */
    private String flightNumber;

    /** Kalkış havaalanı IATA kodu: IST, SAW vb. */
    private String origin;

    /** Varış havaalanı IATA kodu: COV, LHR vb. */
    private String destination;

    /** Kalkış tarih/saati — gruplama anahtarının bir parçası */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime departureDateTime;

    /** Varış tarih/saati — gruplama anahtarının bir parçası */
    @JsonFormat(pattern = "dd-MM-yyyy'T'HH:mm")
    private LocalDateTime arrivalDateTime;

    /** Uçuş fiyatı — cheapest seçimi için kullanılır */
    private BigDecimal price;

    /**
     * Kaynağı belirtir: "PROVIDER_A" veya "PROVIDER_B"
     * Loglama ve izleme için kullanılır.
     */
    private String provider;
}
