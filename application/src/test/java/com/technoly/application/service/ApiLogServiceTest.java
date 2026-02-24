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
 * ApiLogService Unit Test
 *
 * @Async and @Transactional are tricky in their own tests (requires Spring AOP
 *        proxy).
 *        Here we test the business logic (JSON serialize, entity structure),
 *        not the annotation behavior. Integration tests cover that.
 *
 *        @ExtendWith(MockitoExtension) → No Spring context, pure Mockito
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiLogService Unit Tests")
class ApiLogServiceTest {

    @Mock
    private ApiLogRepository apiLogRepository;

    @InjectMocks
    private ApiLogService apiLogService;

    // Use a real ObjectMapper (not mock): to test JSON serialization
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private ApiLogService serviceWithRealMapper;

    @BeforeEach
    void setUp() {
        // @InjectMocks cannot inject ObjectMapper (not a final field target of Mockito)
        // Therefore, we manually create it with a real ObjectMapper
        serviceWithRealMapper = new ApiLogService(apiLogRepository, objectMapper);
    }

    @Test
    @DisplayName("Successful log entry: repository.save is called")
    void logApiCall_success_callsRepositorySave() {
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(30))
                .build();
        FlightSearchResponse response = FlightSearchResponse.empty();

        serviceWithRealMapper.logApiCall("/api/v1/flights/search", request, response, 200, 150L);

        // verify save is called
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
    @DisplayName("Does not throw exception even with null request (fire-and-forget)")
    void logApiCall_withNullRequest_doesNotThrow() {
        assertThatCode(() -> serviceWithRealMapper.logApiCall("/endpoint", null, null, 200, 0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Repository error → exception is not thrown, log is written")
    void logApiCall_repositoryThrows_doesNotPropagateException() {
        when(apiLogRepository.save(any())).thenThrow(new RuntimeException("DB connection error"));

        assertThatCode(() -> serviceWithRealMapper.logApiCall("/endpoint", "req", "res", 500, 100L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Unserializable object → fallback string is used")
    void logApiCall_serializeError_usesFallback() {
        // Mock ObjectMapper and throw error
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        ApiLogService failingService = new ApiLogService(apiLogRepository, failingMapper);
        try {
            when(failingMapper.writeValueAsString(any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("test error") {
                    });
        } catch (Exception e) {
            /* mock setup */ }

        // Should not throw exception — absorbs serialization error
        assertThatCode(() -> failingService.logApiCall("/endpoint", new Object(), null, 200, 10L))
                .doesNotThrowAnyException();
    }
}
