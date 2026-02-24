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
 * GlobalExceptionHandler Unit Test
 *
 * Testing the handler directly without Spring context.
 * Validates RFC 7807 ProblemDetail format:
 * - HTTP status code
 * - title and detail elements
 * - Ensures a non-empty body
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ConstraintViolationException -> 400 Bad Request + ProblemDetail")
    void handleConstraintViolation_returns400() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("cannot be null");

        ConstraintViolationException ex = new ConstraintViolationException(
                "Validation failed", Set.of(violation));

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Error");
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getType()).isNotNull();
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/validation");
    }

    @Test
    @DisplayName("ConstraintViolationException empty violations -> 400 with fallback message")
    void handleConstraintViolation_emptyViolations_returns400WithFallback() {
        ConstraintViolationException ex = new ConstraintViolationException("Error message", Set.of());

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("Error message");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException -> 400 Bad Request + parameter info")
    void handleTypeMismatch_returns400WithParamInfo() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("departureDate");
        when(ex.getValue()).thenReturn("not-a-date");
        when(ex.getMessage()).thenReturn("type mismatch");

        ResponseEntity<ProblemDetail> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Parameter Error");
        assertThat(response.getBody().getDetail()).contains("departureDate");
        assertThat(response.getBody().getDetail()).contains("not-a-date");
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/invalid-parameter");
    }

    @Test
    @DisplayName("Exception (general) -> 500 Internal Server Error + ProblemDetail")
    void handleGeneral_returns500() {
        Exception ex = new RuntimeException("Unexpected exception");

        ResponseEntity<ProblemDetail> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Server Error");
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).contains("unexpected error");
        assertThat(response.getBody().getType().toString())
                .contains("technoly.com/errors/internal-error");
    }
}
