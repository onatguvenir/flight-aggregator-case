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
 * SecurityConfig / RateLimitingFilter Birim Testleri
 *
 * Resilience4j tabanlı çalışmaya dönüştürülmüştür.
 * RateLimiter yapısı doğrulanır.
 */
@DisplayName("Rate Limiting Logic Tests (Resilience4j)")
class SecurityConfigTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Her test için temiz bir RateLimiter: 5 istek limit, 10 saniye periyod
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ZERO) // Token bulamadığında beklemeden dön (blokajsız fail-fast)
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        rateLimiter = registry.rateLimiter("TEST_IP");
    }

    @Test
    @DisplayName("İlk 5 istek başarıyla token tüketmeli")
    void tokenBucketAllowsRequestsUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.acquirePermission())
                    .as("İstek %d token tüketebilmeli", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Kota dolduğunda token tüketimi reddedilmeli")
    void tokenBucketRejectsWhenCapacityExceeded() {
        // Tüm tokenleri tüket
        for (int i = 0; i < 5; i++) {
            rateLimiter.acquirePermission();
        }

        // 6. istek bekleme olmadığından anında false dönmeli
        assertThat(rateLimiter.acquirePermission())
                .as("Kota dolduğunda istek reddedilmeli")
                .isFalse();
    }

    @Test
    @DisplayName("RateLimiter başlangıçta tam kapasitede olmalı")
    void bucketStartsAtFullCapacity() {
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(5);
    }
}
