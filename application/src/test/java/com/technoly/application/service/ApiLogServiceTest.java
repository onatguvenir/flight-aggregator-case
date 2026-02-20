package com.technoly.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.model.FlightSearchResponse;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import com.technoly.infrastructure.persistence.repository.ApiLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * ApiLogService Unit Testi
 *
 * @Async ve @Transactional kendi testlerinde zorlanır (Spring AOP proxy
 *        gerektirir).
 *        Burada business logic'i (JSON serialize, entity yapısı) test ediyoruz,
 *        annotation davranışını değil. Integration testler onu sağlar.
 *
 *        @ExtendWith(MockitoExtension) → Spring context yok, saf Mockito
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiLogService Unit Testleri")
class ApiLogServiceTest {

    @Mock
    private ApiLogRepository apiLogRepository;

    @InjectMocks
    private ApiLogService apiLogService;

    // Gerçek ObjectMapper kullan (mock değil): JSON serialize test edilsin
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private ApiLogService serviceWithRealMapper;

    @BeforeEach
    void setUp() {
        // @InjectMocks ObjectMapper'ı inject edemez (final alan değil Mockito hedefi)
        // Bu yüzden gerçek ObjectMapper ile manual oluşturuyoruz
        serviceWithRealMapper = new ApiLogService(apiLogRepository, objectMapper);
    }

    @Test
    @DisplayName("Başarılı log kaydı: repository.save çağrılır")
    void logApiCall_success_callsRepositorySave() {
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(30))
                .build();
        FlightSearchResponse response = FlightSearchResponse.empty();

        serviceWithRealMapper.logApiCall("/api/v1/flights/search", request, response, 200, 150L);

        // save çağrıldı mı doğrula
        ArgumentCaptor<ApiLogEntity> captor = ArgumentCaptor.forClass(ApiLogEntity.class);
        verify(apiLogRepository, times(1)).save(captor.capture());

        ApiLogEntity saved = captor.getValue();
        assertThat(saved.getEndpoint()).isEqualTo("/api/v1/flights/search");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getDurationMs()).isEqualTo(150L);
        assertThat(saved.getRequest()).contains("IST");
        assertThat(saved.getResponse()).isNotBlank();
    }

    @Test
    @DisplayName("Null request ile de exception fırlatmaz (fire-and-forget)")
    void logApiCall_withNullRequest_doesNotThrow() {
        assertThatCode(() -> serviceWithRealMapper.logApiCall("/endpoint", null, null, 200, 0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Repository hatası → exception fırlatılmaz, log yazılır")
    void logApiCall_repositoryThrows_doesNotPropagateException() {
        when(apiLogRepository.save(any())).thenThrow(new RuntimeException("DB bağlantı hatası"));

        assertThatCode(() -> serviceWithRealMapper.logApiCall("/endpoint", "req", "res", 500, 100L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Serialize edilemeyen nesne → fallback string kullanılır")
    void logApiCall_serializeError_usesFallback() {
        // ObjectMapper mock'la ve hata ver
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        ApiLogService failingService = new ApiLogService(apiLogRepository, failingMapper);
        try {
            when(failingMapper.writeValueAsString(any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("test error") {
                    });
        } catch (Exception e) {
            /* mock setup */ }

        // Exception fırlatmamalı — seriale hatasını absorbe eder
        assertThatCode(() -> failingService.logApiCall("/endpoint", new Object(), null, 200, 10L))
                .doesNotThrowAnyException();
    }
}
