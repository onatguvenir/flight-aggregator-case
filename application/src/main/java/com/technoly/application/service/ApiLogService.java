package com.technoly.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import com.technoly.infrastructure.persistence.repository.ApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API Log Servisi
 *
 * Her REST API isteğini ve yanıtını asenkron olarak PostgreSQL'e kaydeder
 * ve listeleme işlemlerini sayfalı biçimde sunar.
 *
 * Pagination Neden Zorunlu?
 * api_logs tablosu üretim ortamında milyonlarca kayıt içerebilir.
 * findAll() → tüm satırları belleğe çekmek → OOM (Out of Memory) hatası.
 * findAll(Pageable) → yalnızca istenen sayfayı getirir → hafızadan tasarruf.
 *
 * @Async ile bu iş arka plan thread'inde yapılır, kullanıcı yanıtı beklemez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiLogService {

    private final ApiLogRepository apiLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * API çağrısını veritabanına asenkron olarak loglar.
     *
     * @Async: Bu metod kendi thread'inde çalışır, çağıranı bloke etmez.
     *         → @EnableAsync annotation'ı gereklidir
     *         (FlightAggregatorApplication'da).
     *
     * @Transactional: REQUIRES_NEW ile bağımsız transaction.
     *                 → Çağıran başarısız olsa da log yazılır.
     *
     * @param endpoint   Çağrılan endpoint: "/api/v1/flights/search"
     * @param request    Request nesnesi (JSON'a çevrilecek)
     * @param response   Response nesnesi (JSON'a çevrilecek), null olabilir
     * @param statusCode HTTP durum kodu
     * @param durationMs İşlem süresi (milisaniye)
     */
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logApiCall(String endpoint, Object request, Object response,
            Integer statusCode, Long durationMs) {
        try {
            String requestJson = toJson(request);
            String responseJson = toJson(response);

            ApiLogEntity logEntity = ApiLogEntity.builder()
                    .endpoint(endpoint)
                    .request(requestJson)
                    .response(responseJson)
                    .statusCode(statusCode)
                    .durationMs(durationMs)
                    .build();

            apiLogRepository.save(logEntity);

            log.debug("API log kaydedildi: endpoint={}, status={}, duration={}ms",
                    endpoint, statusCode, durationMs);

        } catch (Exception e) {
            // Log işlemi hiçbir zaman exception fırlatmamalı (fire-and-forget)
            log.error("API log kaydedilemedi: {}", e.getMessage(), e);
        }
    }

    // =====================================================================
    // Paginated Query Methods (Production-Safe)
    // =====================================================================

    /**
     * Tüm logları sayfalı olarak listeler.
     *
     * Örnek kullanım: Pageable.of(0, 20, Sort.by("createdAt").descending())
     *
     * @param pageable Sayfa numarası, boyut ve sıralama bilgisi
     * @return Sayfalı log nesneleri
     */
    public Page<ApiLogEntity> getAllLogs(Pageable pageable) {
        return apiLogRepository.findAll(pageable);
    }

    /**
     * Belirli bir endpoint için sayfalı logları listeler.
     *
     * @param endpoint Aranacak endpoint path'i (örn: /api/v1/flights/search)
     * @param pageable Sayfa numarası, boyut ve sıralama bilgisi
     * @return Sayfalı log nesneleri
     */
    public Page<ApiLogEntity> getLogsByEndpoint(String endpoint, Pageable pageable) {
        return apiLogRepository.findByEndpoint(endpoint, pageable);
    }

    // =====================================================================
    // Legacy (Backward Compatible) — Pagination olmadan tüm veri
    // =====================================================================

    /**
     * Tüm logları listeler (sayfalama olmadan).
     * 
     * @deprecated Üretim ortamında OOM riske yol açar; getAllLogs(Pageable)
     *             kullanın.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public List<ApiLogEntity> getAllLogs() {
        return apiLogRepository.findAll();
    }

    /**
     * Belirli bir endpoint için tüm logları listeler (sayfalama olmadan).
     * 
     * @deprecated Üretim ortamında OOM riske yol açar; getLogsByEndpoint(String,
     *             Pageable) kullanın.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public List<ApiLogEntity> getLogsByEndpoint(String endpoint) {
        return apiLogRepository.findByEndpoint(endpoint);
    }

    /**
     * Nesneyi JSON string'e çevirir.
     * Null-safe: null input → "null" string döner.
     */
    private String toJson(Object obj) {
        if (obj == null)
            return "null";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialize hatası: {}", e.getMessage());
            return "\"serialization_error\"";
        }
    }
}
