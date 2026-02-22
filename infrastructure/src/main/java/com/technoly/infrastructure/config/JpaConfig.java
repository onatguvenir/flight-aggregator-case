package com.technoly.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Repository ve Entity Konfigürasyonu
 *
 * Neden ayrı bir @Configuration sınıfında?
 * -------------------------------------------------------
 * 
 * @EnableJpaRepositories ve @EntityScan, FlightAggregatorApplication içinde
 *                        tanımlandığında, @WebMvcTest testleri bu
 *                        annotasyonları da yüklemeye çalışır.
 * @WebMvcTest JPA auto-config'i devre dışı bırakırken @EnableJpaRepositories
 *             aktif olduğunda "No bean named 'entityManagerFactory' available"
 *             hatasını
 *             tetikler.
 *
 *             Bu @Configuration sınıfı infrastructure modülünde tutularak test
 *             katmanında
 * @WebMvcTest'in bunu otomatik yüklememesi sağlanır.
 *                (Spring Boot'un @WebMvcTest'i bu sınıfı otomatik dışarıda
 *                bırakır çünkü
 * @Repository / @Service annotasyonu taşımıyor.)
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.technoly.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.technoly.infrastructure.persistence.entity")
public class JpaConfig {
    // Marker configuration sınıfı — bean tanımı gerekmez
}
