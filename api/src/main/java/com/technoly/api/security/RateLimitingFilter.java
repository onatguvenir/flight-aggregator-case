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
 * IP-Based Rate Limiting Filter using Resilience4j
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${rate-limit.capacity:60}")
    private int capacity;

    private final Duration limitRefreshPeriod = Duration.ofMinutes(1);
    private final Duration timeoutDuration = Duration.ZERO;
    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitingFilter() {
        this.rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        RateLimiter rateLimiter = resolveRateLimiter(clientIp);

        boolean permissionAcquired = rateLimiter.acquirePermission();

        if (permissionAcquired) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(rateLimiter.getMetrics().getAvailablePermissions()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("[RateLimit] IP: {} exceeded limit. Path: {}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-Rate-Limit-Retry-After-Seconds", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter()
                    .write("""
                            {"error": "Too Many Requests", "message": "Rate limit exceeded. Please try again later.", "status": 429}
                            """);
        }
    }

    private RateLimiter resolveRateLimiter(String clientIp) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(limitRefreshPeriod)
                .limitForPeriod(capacity)
                .timeoutDuration(timeoutDuration)
                .build();
        return rateLimiterRegistry.rateLimiter(clientIp, config);
    }

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
