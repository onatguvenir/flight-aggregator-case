package com.technoly.api.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * Global Exception Handler
 *
 * @RestControllerAdvice: Tüm @RestController'lardan çıkan exception'ları
 *                        yakalar.
 *
 *                        ResponseEntityExceptionHandler extend ediyoruz çünkü:
 *                        - Spring MVC'nin default exception mapping'lerini
 *                        (400, 405, 415 vb.) devralır
 *                        - Catch-all Exception.class handler, Spring'in
 *                        built-in exception'larını
 *                        ezemez (MissingServletRequestParameterException vb.
 *                        önce parent'a gider)
 *
 *                        RFC 7807 Problem Details (Spring 6+):
 *                        Tüm hatalar standart JSON formatında döner:
 *                        { "type": "...", "title": "...", "status": 400,
 *                        "detail": "..." }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Zorunlu @RequestParam eksik olduğunda Spring MVC bu exception'ı fırlatır.
     * Parent class bunu 400'e map eder ama özel mesaj vermek için override
     * ediyoruz.
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Zorunlu parametre eksik: {}", ex.getParameterName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "'" + ex.getParameterName() + "' parametresi zorunludur.");
        problem.setTitle("Eksik Parametre");
        problem.setType(URI.create("https://api.technoly.com/errors/missing-parameter"));
        problem.setProperty("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Bean Validation hataları (@NotBlank, @NotNull, @Future gibi @RequestParam
     * validasyonları — @Validated + ConstraintViolationException)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Validation hatası: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getConstraintViolations().stream()
                        .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                        .findFirst()
                        .orElse(ex.getMessage()));
        problem.setTitle("Validation Hatası");
        problem.setType(URI.create("https://api.technoly.com/errors/validation"));
        problem.setProperty("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Request parametre tip uyuşmazlıkları (örn: LocalDateTime parse hatası)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametre tip hatası: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "'" + ex.getName() + "' parametresi için geçersiz değer: '" + ex.getValue() +
                        "'. Beklenen format: dd-MM-yyyy'T'HH:mm");
        problem.setTitle("Parametre Hatası");
        problem.setType(URI.create("https://api.technoly.com/errors/invalid-parameter"));

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Beklenmeyen tüm hatalar — son çare handler.
     * ResponseEntityExceptionHandler'dan extend edilince Spring MVC'nin
     * kendi exception'ları buraya düşmez (doğru davranış).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Beklenmeyen hata: {}", ex.getMessage(), ex);
        ex.printStackTrace();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Sunucuda beklenmeyen bir hata oluştu. Lütfen tekrar deneyiniz.");
        problem.setTitle("Sunucu Hatası");
        problem.setType(URI.create("https://api.technoly.com/errors/internal-error"));

        return ResponseEntity.internalServerError().body(problem);
    }
}
