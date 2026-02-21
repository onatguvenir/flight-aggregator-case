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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Üçüncü Endpoint - API Logları REST Controller
 *
 * Diğer iki endpoint'e yapılan request ve responseların listelenmesini sağlar.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "API Logs", description = "Veritabanına asenkron olarak kaydedilmiş request ve response loglarını sorgular.")
public class ApiLogController {

    private final ApiLogService apiLogService;

    @GetMapping
    @Operation(summary = "Tüm logları listeler", description = """
            Veritabanındaki PostgreSQL log tablosuna atılmış olan tüm request ve response kayıtlarını getirir.
            Opsiyonel olarak endpoint path'ine göre filtreleme yapılabilir.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loglar başarıyla listelendi", content = @Content(schema = @Schema(implementation = ApiLogEntity.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek parametreleri"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    public ResponseEntity<List<ApiLogEntity>> getLogs(
            @Parameter(description = "Sorgulanacak endpoint, örneğin: /api/v1/flights/search", required = false) @RequestParam(required = false) String endpoint) {

        log.info("Log listeleme isteği alındı. Endpoint filtresi: {}", endpoint);

        List<ApiLogEntity> logs;
        if (endpoint != null && !endpoint.isBlank()) {
            logs = apiLogService.getLogsByEndpoint(endpoint);
        } else {
            logs = apiLogService.getAllLogs();
        }

        log.info("{} adet log bulundu.", logs.size());
        return ResponseEntity.ok(logs);
    }
}
