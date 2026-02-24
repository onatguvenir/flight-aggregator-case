package com.technoly.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@EnableCaching
public class RedisConfig {

        private static final DateTimeFormatter CUSTOM_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm");

        @Value("${cache.flight.ttl-minutes:5}")
        private long flightCacheTtlMinutes;

        /**
         * JSON serializer for Redis.
         * It is based on Spring Boot's ObjectMapper and only adds
         * a custom format for LocalDateTime.
         */
        @Bean
        public RedisSerializer<Object> redisValueSerializer(ObjectMapper baseMapper) {

                ObjectMapper mapper = baseMapper.copy();

                SimpleModule module = new SimpleModule();

                module.addSerializer(LocalDateTime.class,
                                new LocalDateTimeSerializer(CUSTOM_FORMATTER));

                module.addDeserializer(LocalDateTime.class,
                                new LocalDateTimeDeserializer(CUSTOM_FORMATTER));

                mapper.registerModule(module);

                // Use string instead of Timestamp
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                return new GenericJackson2JsonRedisSerializer(mapper);
        }

        @Bean
        public CacheManager cacheManager(
                        RedisConnectionFactory connectionFactory,
                        RedisSerializer<Object> redisValueSerializer) {

                RedisCacheConfiguration config = RedisCacheConfiguration
                                .defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .disableCachingNullValues()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(redisValueSerializer));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                .withCacheConfiguration("flightSearch",
                                                config.entryTtl(Duration.ofMinutes(flightCacheTtlMinutes)))
                                .withCacheConfiguration("cheapestFlights",
                                                config.entryTtl(
                                                                Duration.ofMinutes(flightCacheTtlMinutes * 2))) // keep
                                // for twice
                                // the duration
                                .withCacheConfiguration("staticData", config.entryTtl(Duration.ofDays(1))) // 1
                                // day
                                .build();
        }
}