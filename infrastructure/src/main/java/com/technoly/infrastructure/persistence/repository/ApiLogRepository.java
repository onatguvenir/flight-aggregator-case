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
 * PagingAndSortingRepository comes integrated with JpaRepository.
 * The added Page<> based methods eliminate the OOM (Out of Memory) risk
 * in production scenarios: instead of pulling the whole table into memory,
 * data is read page by page.
 *
 * findBy...(Pageable) methods: Spring Data JPA automatically generates
 * LIMIT/OFFSET and ORDER BY SQL statements from Pageable.
 */
@Repository
public interface ApiLogRepository extends JpaRepository<ApiLogEntity, Long> {

    /**
     * Returns all log records for a specific endpoint.
     * SQL equivalent: SELECT * FROM api_logs WHERE endpoint = ?
     */
    List<ApiLogEntity> findByEndpoint(String endpoint);

    /**
     * Returns paginated log records for a specific endpoint.
     * Example: ?page=0&size=20&sort=createdAt,desc
     */
    Page<ApiLogEntity> findByEndpoint(String endpoint, Pageable pageable);

    /**
     * Returns log records created within a time range.
     * SQL equivalent: SELECT * FROM api_logs WHERE created_at BETWEEN ? AND ?
     */
    List<ApiLogEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Returns records with a specific HTTP status code.
     * Example: To track failed calls (statusCode >= 400).
     */
    List<ApiLogEntity> findByStatusCode(Integer statusCode);
}
