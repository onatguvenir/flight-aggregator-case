package com.technoly.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP Bazlı Rate Limiting Filtresi (Token Bucket Algoritması)
 *
 * Nasıl Çalışır?
 * Bucket4j, Token Bucket algoritmasını uygular:
 * - Her IP adresi için ayrı bir "kova" (bucket) tutulur.
 * - Kova belirli kapasitede token barındırır (örn: 60 istek).
 * - Her API isteği 1 token tüketir.
 * - Kova, belirli aralıklarla (örn: dakikada 60) yeniden dolar.
 * - Kova boşaldığında 429 Too Many Requests döner.
 *
 * Neden ConcurrentHashMap?
 * - Prod'da Redis Bucket4j extension'ı ile değiştirilmeli (node başına
 * eşitleme).
 * - Bu implementasyon tek node için uygundur (Docker Compose ortamı).
 * - Çoklu node: bucket4j-redis veya bucket4j-hazelcast kullanılmalı.
 *
 * OncePerRequestFilter:
 * - Her HTTP isteği için yalnızca bir kez çalışır (forward/include tekrarı
 * olmaz).
 * - Spring Security'nin filterChain'ine otomatik eklenir (@Component ile).
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /**
     * IP başına dakikada izin verilen maksimum istek sayısı.
     * .env veya application.yml'den overridable.
     */
    @Value("${rate-limit.capacity:60}")
    private long capacity;

    @Value("${rate-limit.refill-per-minute:60}")
    private long refillPerMinute;

    /**
     * IP → Bucket eşlemesi.
     * Her benzersiz client IP'si için bir bucket oluşturulur.
     * ConcurrentHashMap: thread-safe, yüksek eşzamanlılık için uygun.
     */
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        Bucket bucket = resolveBucket(clientIp);

        // Token tüket — eğer yeterli token yoksa false döner
        if (bucket.tryConsume(1)) {
            // Token tüketildi → isteği devam ettir
            long remainingTokens = bucket.getAvailableTokens();
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
            filterChain.doFilter(request, response);
        } else {
            // Kota doldu → 429 Too Many Requests
            log.warn("[RateLimit] IP: {} limit aşıldı. Path: {}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", "60");
            response.setContentType("application/json");
            response.getWriter()
                    .write("""
                            {"error": "Too Many Requests", "message": "Dakika başına istek limitiniz doldu. Lütfen 60 saniye sonra tekrar deneyin.", "status": 429}
                            """);
        }
    }

    /**
     * IP için mevcut bucket'ı döndürür, yoksa yeni bucket oluşturur.
     * computeIfAbsent() thread-safe atomik bucket oluşturma sağlar.
     */
    private Bucket resolveBucket(String clientIp) {
        return ipBuckets.computeIfAbsent(clientIp, this::createNewBucket);
    }

    /**
     * Yeni bir Token Bucket oluşturur.
     *
     * Bandwidth.classic(): Sabit kapasiteli, düzenli yenilenen bandwidth.
     * - capacity: Kovadaki maksimum token sayısı (burst limit)
     * - Refill.intervally(): Belirtilen süre sonunda kovayı tamamen doldurur.
     * (dakika başında 60 token → greedy değil, interval-based)
     */
    private Bucket createNewBucket(String clientIp) {
        // Bucket4j 8.x modern fluent API (Refill/Bandwidth.classic() deprecated'dır)
        // simple(): Kapasiteyi bir kez doldurup dakikada refillPerMinute token ekler
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Client IP adresini çıkarır.
     * Reverse proxy (Nginx, LB) durumunda X-Forwarded-For header'ı kontrol edilir.
     * X-Real-IP: single hop proxy için
     * RemoteAddr: doğrudan bağlantı için
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — ilk IP gerçek client
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
