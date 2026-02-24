package com.technoly.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Flight Aggregator - Main Application Class
 */
@SpringBootApplication(scanBasePackages = {
        "com.technoly.api",
        "com.technoly.application",
        "com.technoly.infrastructure",
        "com.technoly.domain"
})
@EnableAsync
public class FlightAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightAggregatorApplication.class, args);
    }
}
