package com.technoly.infrastructure.persistence.repository;

import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Log JPA Repository
 *
 * Spring Data JPA, bu interface için otomatik implementasyon üretir.
 * JpaRepository<ApiLogEntity, Long>:
 * - ApiLogEntity: entity tipi
 * - Long: primary key tipi
 *
 * findBy... metodları: Spring Data JPA, metod adından SQL oluşturur.
 * Bu yaklaşım, tekrar eden JPQL yazımını önler (Convention over Configuration).
 */
@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    /**
     * Belirli bir endpoint için tüm log kayıtlarını döner.
     * SQL eşdeğeri: SELECT * FROM api_logs WHERE endpoint = ?
     */
    List<ApiLogEntity> findByEndpoint(String endpoint);

    /**
     * Zaman aralığında oluşturulan log kayıtlarını döner.
     * SQL eşdeğeri: SELECT * FROM api_logs WHERE created_at BETWEEN ? AND ?
     */
    List<ApiLogEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Belirli bir HTTP durum koduna sahip kayıtları döner.
     * Örnek: Hatalı çağrıları (statusCode >= 400) izlemek için.
     */
    List<ApiLogEntity> findByStatusCode(Integer statusCode);
}
