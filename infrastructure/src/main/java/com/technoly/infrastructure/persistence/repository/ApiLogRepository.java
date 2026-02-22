package com.technoly.infrastructure.persistence.repository;

import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Log JPA Repository
 *
 * JpaRepository ile PagingAndSortingRepository birleşik olarak gelmektedir.
 * Eklenen Page<> tabanlı metodlar production senaryolarında OOM (Out of
 * Memory) riskini ortadan kaldırır: tüm tabloyu belleğe çekmek yerine
 * sayfa sayfa veri okunur.
 *
 * findBy...(Pageable) metodları: Spring Data JPA, Pageable'dan LIMIT/OFFSET
 * ve ORDER BY SQL cümlelerini otomatik üretir.
 */
@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    /**
     * Belirli bir endpoint için tüm log kayıtlarını döner.
     * SQL eşdeğeri: SELECT * FROM api_logs WHERE endpoint = ?
     */
    List<ApiLogEntity> findByEndpoint(String endpoint);

    /**
     * Belirli bir endpoint için sayfalı log kayıtlarını döner.
     * Örnek: ?page=0&size=20&sort=createdAt,desc
     */
    Page<ApiLogEntity> findByEndpoint(String endpoint, Pageable pageable);

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
