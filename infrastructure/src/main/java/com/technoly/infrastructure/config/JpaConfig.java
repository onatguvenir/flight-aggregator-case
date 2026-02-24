package com.technoly.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Repository and Entity Configuration
 *
 * Why in a separate @Configuration class?
 * -------------------------------------------------------
 * 
 * When @EnableJpaRepositories and @EntityScan are defined in
 * FlightAggregatorApplication,
 * 
 * @WebMvcTest tests try to load these annotations as well.
 *             While @WebMvcTest disables JPA auto-config,
 *             when @EnableJpaRepositories
 *             is active, it triggers a "No bean named 'entityManagerFactory'
 *             available" error.
 *
 *             By keeping this @Configuration class in the infrastructure
 *             module, we ensure that
 * @WebMvcTest does not load it automatically in the test layer.
 *             (Spring Boot's @WebMvcTest automatically excludes this class
 *             because
 *             it doesn't carry a @Repository / @Service annotation.)
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.technoly.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.technoly.infrastructure.persistence.entity")
public class JpaConfig {
    // Marker configuration class — no bean definition required
}
