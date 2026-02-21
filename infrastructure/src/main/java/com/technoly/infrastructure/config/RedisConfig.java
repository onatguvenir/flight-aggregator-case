package com.technoly.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Cache Konfigürasyonu
 *
 * Neden Redis cache?
 * SOAP çağrıları (provider A ve B) görece pahalıdır:
 * - Network round-trip: ~50-200ms
 * - XML parse overhead
 * - Provider rate limits olabilir
 *
 * Aynı kalkış/varış/tarih kombinasyonu için tekrar eden aramalarda
 * Redis cache kullanılarak SOAP çağrısı bypass edilir.
 *
 * Cache strategy: TTL-based expiration
 * - Uçuş bilgileri çok hızlı değişmez (saatlik güncelleme varsayımı)
 * - 5 dakikalık TTL yeterlidir (configureable)
 *
 * @EnableCaching: Spring'in @Cacheable, @CacheEvict gibi
 *                 annotation'larını aktif eder. Bu annotation olmadan cache
 *                 annotation'ları çalışmaz.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Cache TTL değeri (varsayılan: 5 dakika).
     * application.yml'den override edilebilir: cache.flight.ttl-minutes=10
     */
    @Value("${cache.flight.ttl-minutes:5}")
    private long flightCacheTtlMinutes;

    /**
     * RedisCacheManager: Spring Cache abstraction'ını Redis'e bağlar.
     *
     * Konfigürasyon:
     * - defaultCacheConfig(): Tüm cache'ler için varsayılan ayarlar
     * - entryTtl: Cache girdilerinin yaşam süresi
     * - serializeValuesWith: JSON serializer (ObjectMapper ile LocalDateTime
     * desteği)
     * - disableCachingNullValues: null değerleri cache'lemez (defensive)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(flightCacheTtlMinutes))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper())))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    /**
     * Generic RedisTemplate: Programatik Redis operasyonları için.
     * Key: String, Value: JSON (Object)
     *
     * @Cacheable annotation yerine programatik cache kullanılmak
     *            istendiğinde bu template enjekte edilir.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper()));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper()));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Jackson ObjectMapper: Sadece Redis'te JSON serialization için özel nesne.
     * 
     * @Bean yapmıyoruz çünkü @Bean yaparsak Spring Web'in genel ObjectMapper'ını
     *       ezer ve API yanıtlarında Java sınıf isimleri sızar.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }
}
