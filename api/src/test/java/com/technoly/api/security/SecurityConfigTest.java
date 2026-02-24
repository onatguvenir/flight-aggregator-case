package com.technoly.api.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityConfig / RateLimitingFilter Unit Tests
 *
 * Validates Resilience4j RateLimiter logic.
 */
@DisplayName("Rate Limiting Logic Tests (Resilience4j)")
class SecurityConfigTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO)
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        rateLimiter = registry.rateLimiter("TEST_IP");
    }

    @Test
    @DisplayName("First 5 requests should consume tokens successfully")
    void tokenBucketAllowsRequestsUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.acquirePermission())
                    .as("Request %d should consume a token", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Requests should be rejected when capacity is exceeded")
    void tokenBucketRejectsWhenCapacityExceeded() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermission();
        }

        assertThat(rateLimiter.acquirePermission())
                .as("Request should be rejected when capacity is full")
                .isFalse();
    }

    @Test
    @DisplayName("RateLimiter should start at full capacity")
    void bucketStartsAtFullCapacity() {
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(5);
    }
}
