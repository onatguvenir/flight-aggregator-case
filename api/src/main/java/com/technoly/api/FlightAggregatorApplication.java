package com.technoly.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Flight Aggregator - Ana Uygulama Sınıfı
 *
 * @SpringBootApplication: 3 annotation'ı birleştirir:
 *                         - @Configuration: Bean tanımları
 *                         - @EnableAutoConfiguration: Spring Boot auto-config
 *                         - @ComponentScan: com.technoly.api ve alt paketleri
 *                         tara
 *
 *                         Modüler monolitte component scan nasıl çalışır?
 *                         → api modülü, infrastructure ve application
 *                         modüllerini
 *                         dependency olarak içerir. Spring Boot, classpath'teki
 *                         tüm
 *                         @Component, @Service, @Repository bean'lerini
 *                         otomatik bulur.
 *                         Yani tüm modüllerden bean'ler tek context'te
 *                         toplanır.
 *
 * @EnableAsync: @Async annotation'ını aktif eder.
 *               ApiLogService.logApiCall() metodunun asenkron çalışması için
 *               gereklidir.
 *               Bu olmadan @Async metodlar senkron çalışır.
 *
 *               scanBasePackages: Tüm modüllerin paketleri dahil edilir.
 */
@SpringBootApplication(scanBasePackages = {
        "com.technoly.api",
        "com.technoly.application",
        "com.technoly.infrastructure",
        "com.technoly.domain"
})
@EnableJpaRepositories(basePackages = "com.technoly.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.technoly.infrastructure.persistence.entity")
@EnableAsync
public class FlightAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightAggregatorApplication.class, args);
    }
}
