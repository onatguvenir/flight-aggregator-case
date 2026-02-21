package com.technoly.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider API kütüphaneleri için Configuration sınıfı.
 * Dış bağımlılıkların (Provider A ve Provider B SearchService sınıflarının)
 * Spring Application Context içerisine Bean olarak kaydedilmesini sağlar.
 * 
 * Bu sayede sınıflar test edilebilir hale gelir (Dependency Inversion /
 * Constructor Injection).
 */
@Configuration
public class ProviderConfig {

    @Bean(name = "providerASearchService")
    public com.flightprovidera.service.SearchService providerASearchService() {
        return new com.flightprovidera.service.SearchService();
    }

    @Bean(name = "providerBSearchService")
    public com.flightproviderb.service.SearchService providerBSearchService() {
        return new com.flightproviderb.service.SearchService();
    }
}
