package com.technoly.api.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * IP Bazlı Rate Limiting Filtresi (Resilience4j)
 *
 * Nasıl Çalışır?
 * Resilience4j RateLimiter, Token Bucket veya Semaphore prensibi ile çalışır:
 * - Her benzersiz IP adresi için 'RateLimiterRegistry' kullanılarak dinamik
 * RateLimiter yaratılır.
 * - Saniyedeki/dakikadaki izin verilen istek süresi (limitRefreshPeriod)
 * konfigüre edilebilir.
 * - Limit aşıldığında 429 Too Many Requests HTTP durumu döndürülür.
 *
 * Neden Resilience4j'ye Geçildi?
 * - Projenin genel mimarisinde (Retry, CircuitBreaker, Bulkhead) zaten
 * Resilience4j kullanılıyordu.
 * - Tech-stack homojenizasyonu sağlandı ve 3. parti (Bucket4j) bağımlılığı çöpe
 * atıldı.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /**
     * IP başına sınırlandırılan periyotta izin verilen kapasite.
     */
    @Value("${rate-limit.capacity:60}")
    private int capacity;

    /**
     * RateLimiter yenilenme süresi. Default olarak Bucket4J uyumluluğu adına
     * Dakikada 60 kabul edelim, bu 1 dakikalık pencere anlamına gelir.
     */
    private final Duration limitRefreshPeriod = Duration.ofMinutes(1);

    /**
     * Bekleme süresi. Rate limitten geçmeyen anlık bloklansın (0)
     */
    private final Duration timeoutDuration = Duration.ZERO;

    /**
     * Dinamik olarak çalışma zamanında (Runtime) yeni konfigurasyonlu RateLimiter
     * nesneleri
     * yaratma ve saklama görevini üstlenen Resilience4j Registry'si.
     */
    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitingFilter() {
        // Registry varsayılan olarak in-memory map tabanlıdır.
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        RateLimiter rateLimiter = resolveRateLimiter(clientIp);

        // İzin al. acquirePermission() true dönerse engelleme yoktur.
        boolean permissionAcquired = rateLimiter.acquirePermission();

        if (permissionAcquired) {
            // İzin alındı, X-Rate-Limit gibi başlıkları passlayabilir, devam ediyoruz...
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(rateLimiter.getMetrics().getAvailablePermissions()));
            filterChain.doFilter(request, response);
        } else {
            // Kota doldu → 429 Too Many Requests
            log.warn("[RateLimit] IP: {} limit aşıldı. Path: {}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter()
                    .write("""
                            {"error": "Too Many Requests", "message": "Dakika başına istek limitiniz doldu. Lütfen 60 saniye sonra tekrar deneyin.", "status": 429}
                            """);
        }
    }

    /**
     * Gelen Client IP'si için uygun RateLimiter nesnesini bulur.
     * Yoksa, dinamik şekilde yeni ayarlar üreterek Registry içinde kaydeder.
     */
    private RateLimiter resolveRateLimiter(String clientIp) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(limitRefreshPeriod)
                .limitForPeriod(capacity)
                .timeoutDuration(timeoutDuration)
                .build();
        return rateLimiterRegistry.rateLimiter(clientIp, config);
    }

    /**
     * Client IP adresini çıkarır.
     * Reverse proxy (Nginx, LB) durumunda X-Forwarded-For header'ı kontrol edilir.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
