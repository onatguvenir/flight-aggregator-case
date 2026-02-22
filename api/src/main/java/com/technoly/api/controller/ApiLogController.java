package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Üçüncü Endpoint — API Logları REST Controller
 *
 * Sayfalı (paginated) log listeleme:
 * - ?page=0&size=20 → ilk 20 log
 * - ?page=1&size=10 → 11-20. loglar
 * - ?sortBy=createdAt&sortDir=desc → tarihe göre ters sıra
 * - ?endpoint=/api/v1/flights/search → endpoint filtresi
 *
 * Neden Pagination zorunlu?
 * Üretim ortamında api_logs tablosu milyonlarca kayıt barındırabilir.
 * Tüm veriyi bir seferde döndürmek JVM heap'ini tüketir (OOM riski).
 * Page<T> ile yalnızca istenen dilim döndürülür.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "API Logs", description = "Veritabanına asenkron olarak kaydedilmiş request ve response loglarını sayfalı biçimde sorgular.")
public class ApiLogController {

    private final ApiLogService apiLogService;

    /**
     * Sayfalı log listeleme.
     *
     * @param endpoint Opsiyonel endpoint filtresi (/api/v1/flights/search)
     * @param page     Sayfa numarası (0-tabanlı, varsayılan: 0)
     * @param size     Sayfa boyutu (varsayılan: 20, max: 100)
     * @param sortBy   Sıralama alanı (varsayılan: createdAt)
     * @param sortDir  Sıralama yönü: asc | desc (varsayılan: desc)
     * @return Sayfalı log nesneleri + meta bilgiler (totalElements, totalPages vb.)
     */
    @GetMapping
    @Operation(summary = "Logları sayfalı olarak listeler", description = """
            Veritabanındaki API log kayıtlarını sayfa sayfa döndürür.
            Opsiyonel endpoint filtresi ve sıralama parametreleri desteklenir.

            **Örnek:** `/api/v1/logs?page=0&size=20&sortBy=createdAt&sortDir=desc`
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loglar başarıyla listelendi", content = @Content(schema = @Schema(implementation = ApiLogEntity.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek parametreleri"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<Page<ApiLogEntity>> getLogs(
            @Parameter(description = "Sorgulanacak endpoint, örneğin: /api/v1/flights/search") @RequestParam(required = false) String endpoint,

            @Parameter(description = "Sayfa numarası (0 tabanlı)") @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Sayfa başına kayıt sayısı (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Sıralama alanı: createdAt | endpoint | statusCode | durationMs") @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sıralama yönü: asc | desc") @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Log listeleme isteği. endpoint={}, page={}, size={}, sort={} {}",
                endpoint, page, size, sortBy, sortDir);

        // Sıralama yönü belirleme — defensive: yanlış değer gelirse varsayılan desc
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // PageRequest: page numarası, boyut, sıralama alanı ve yonu
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<ApiLogEntity> logs;
        if (endpoint != null && !endpoint.isBlank()) {
            logs = apiLogService.getLogsByEndpoint(endpoint, pageable);
        } else {
            logs = apiLogService.getAllLogs(pageable);
        }

        log.info("Toplam {} kayıttan {}/{} sayfa döndürüldü.",
                logs.getTotalElements(), page, logs.getTotalPages());

        return ResponseEntity.ok(logs);
    }
}
