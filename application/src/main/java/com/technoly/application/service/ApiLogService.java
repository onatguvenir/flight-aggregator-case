package com.technoly.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import com.technoly.infrastructure.persistence.repository.ApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API Log Servisi
 *
 * Her REST API isteğini ve yanıtını asenkron olarak PostgreSQL'e kaydeder.
 *
 * Neden @Async?
 * DB yazma işlemi, HTTP response süresini etkilememeli.
 * 
 * @Async ile bu iş arka plan thread'inde yapılır, kullanıcı yanıtı beklemez.
 *        Örneğin: SOAP çağrısı 200ms + Log yazma 20ms = 220ms olmak yerine
 *        SOAP çağrısı 200ms (log parallel) = 200ms olur.
 *
 *        Neden @Transactional?
 *        DB yazma atomik olmalı. Hata durumunda rollback yapılır.
 *        propagation=REQUIRES_NEW: Çağıran transaction'dan bağımsız çalışır.
 *        (Çağıran transaction başarısız olsa bile log yazılır)
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
     *                 Defensive programming: JSON dönüşüm hataları yakalanır,
     *                 log işlemi hiçbir zaman ana akışı bozmamalıdır.
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

    /**
     * Nesneyi JSON string'e çevirir.
     * Null-safe: null input → "null" string döner
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
