package com.technoly.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * API Log JPA Entity
 *
 * Her REST API çağrısının istek ve yanıt bilgilerini PostgreSQL'de saklar.
 * Hem request hem de response JSON formatında JSONB kolonunda tutulur.
 *
 * Neden JSONB?
 * 1. Esnek schema: Request/response yapısı değişebilir, migration gerekmez
 * 2. PostgreSQL index desteği: JSONB alanlar üzerinde GIN index kurulabilir
 * 3. Query desteği: WHERE clause'da JSON path sorgusu yapılabilir
 * Örnek: SELECT * FROM api_logs WHERE request->>'origin' = 'IST'
 * 4. TEXT'e göre daha iyi performans (parse edilmiş binary format)
 *
 * @Entity: Hibernate bu sınıfı veritabanı tablosu olarak yönetir
 * @Table: Tablo adı ve index tanımları
 * @Column(columnDefinition = "jsonb"): PostgreSQL JSONB tipi
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "api_logs", indexes = {
        // created_at üzerinde index: zaman aralığı sorgularında performans
        @Index(name = "idx_api_logs_created_at", columnList = "created_at"),
        // endpoint üzerinde index: endpoint spesifik sorgular için
        @Index(name = "idx_api_logs_endpoint", columnList = "endpoint")
})
public class ApiLogEntity {

    /**
     * Primary Key: PostgreSQL BIGSERIAL (auto-increment BigInteger).
     * IDENTITY strategy: DB tarafında üretilir, JPA sadece okur.
     * Neden BIGSERIAL yerine UUID değil? → Sequence daha performanslı,
     * sıralı insert'ler için daha uygundur.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Çağrılan REST endpoint: örn "/api/v1/flights/search"
     * nullable=false: Her log kaydında endpoint zorunludur.
     */
    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    /**
     * JSON formatında HTTP request body/params.
     * @JdbcTypeCode(SqlTypes.JSON): Hibernate 6'da veritabanına uygun JSON tipi
     * seçer.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request", nullable = false)
    private String request;

    /**
     * JSON formatında HTTP response body.
     * nullable=true: Hata durumunda response olmayabilir.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response")
    private String response;

    /** HTTP durum kodu: 200, 400, 500 vb. */
    @Column(name = "status_code")
    private Integer statusCode;

    /** İşlem süresi (milisaniye): performans takibi için */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Kayıt oluşturma zamanı.
     * updatable=false: Bu alan bir kez set edilir, asla güncellenmez.
     * 
     * @PrePersist ile otomatik set edilir.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA persist öncesi otomatik olarak zaman damgasını set eder */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
