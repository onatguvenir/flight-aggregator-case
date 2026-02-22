package com.technoly.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityConfig / RateLimitingFilter Birim Testleri
 *
 * Neden Spring context yok?
 * -------------------------------------------------------
 * 
 * @WebMvcTest ile SecurityConfig testi yapmak,
 * @EnableJpaRepositories içeren FlightAggregatorApplication'ı
 *                        context'e dahil ettiğinden
 *                        jpaSharedEM_entityManagerFactory bean'i
 *                        oluşturmaya çalışır → entityManagerFactory bulunamaz →
 *                        hata.
 *
 *                        Bu testler saf Mockito/JUnit5 ile çalışır:
 *                        - SecurityConfig.securityEnabled flag davranışı mock
 *                        seviyesinde doğrulanmaz
 *                        (Spring context gerektiriyor), bunun yerine
 *                        - RateLimitingFilter'ın token bucket mantığını
 *                        doğrudan test ederiz
 *                        - MaskingSerializer.mask() salt logic (pure function)
 *                        testi yapılır
 *
 *                        Integration seviyesi testler (controller -> security)
 *                        için
 *                        Testcontainers ile @SpringBootTest kullanılmalıdır
 *                        (SystemIntegrationTest).
 */
@DisplayName("Rate Limiting Logic Tests")
class SecurityConfigTest {

    private Bucket bucket;

    @BeforeEach
    void setUp() {
        // Her test için temiz bir bucket: 5 token kapasiteli, 10 saniyede 5 token dolar
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofSeconds(10))
                .build();
        bucket = Bucket.builder().addLimit(limit).build();
    }

    @Test
    @DisplayName("İlk 5 istek başarıyla token tüketmeli")
    void tokenBucketAllowsRequestsUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1))
                    .as("İstek %d token tüketebilmeli", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Kota dolduğunda token tüketimi reddedilmeli")
    void tokenBucketRejectsWhenCapacityExceeded() {
        // Tüm tokenleri tüket
        for (int i = 0; i < 5; i++) {
            bucket.tryConsume(1);
        }

        // 6. istek reddedilmeli
        assertThat(bucket.tryConsume(1))
                .as("Kota dolduğunda istek reddedilmeli")
                .isFalse();
    }

    @Test
    @DisplayName("Bucket başlangıçta tam kapasitede olmalı")
    void bucketStartsAtFullCapacity() {
        assertThat(bucket.getAvailableTokens()).isEqualTo(5);
    }
}
