package com.technoly.api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler Unit Testi
 *
 * Exception handler'ı Spring context olmadan doğrudan test ediyoruz.
 * Handler saf Java sınıfı gibi kullanılabilir → hızlı, bağımsız test.
 *
 * RFC 7807 ProblemDetail formatını doğrularız:
 * - HTTP status kodu
 * - title ve detail alanları
 * - Boş dönen body yok
 */
@DisplayName("GlobalExceptionHandler Unit Testleri")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ConstraintViolationException → 400 Bad Request + ProblemDetail")
    void handleConstraintViolation_returns400() {
        // Mock ConstraintViolationException
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("boş olamaz");

        ConstraintViolationException ex = new ConstraintViolationException(
                "Validation failed", Set.of(violation));

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Hatası");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getType()).isNotNull();
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/validation");
    }

    @Test
    @DisplayName("ConstraintViolationException boş violations → fallback message ile 400")
    void handleConstraintViolation_emptyViolations_returns400WithFallback() {
        ConstraintViolationException ex = new ConstraintViolationException("Hata mesajı", Set.of());

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("Hata mesajı");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400 Bad Request + parametre adı")
    void handleTypeMismatch_returns400WithParamInfo() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("departureDate");
        when(ex.getValue()).thenReturn("not-a-date");
        when(ex.getMessage()).thenReturn("type mismatch");

        ResponseEntity<ProblemDetail> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Parametre Hatası");
        assertThat(response.getBody().getDetail()).contains("departureDate");
        assertThat(response.getBody().getDetail()).contains("not-a-date");
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/invalid-parameter");
    }

    @Test
    @DisplayName("Exception (genel) → 500 Internal Server Error + ProblemDetail")
    void handleGeneral_returns500() {
        Exception ex = new RuntimeException("Beklenmeyen bir şey oldu");

        ResponseEntity<ProblemDetail> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Sunucu Hatası");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).contains("beklenmeyen bir hata");
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/internal-error");
    }
}
