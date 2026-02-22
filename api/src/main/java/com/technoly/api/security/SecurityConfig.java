package com.technoly.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT / OAuth2 Resource Server Güvenlik Konfigürasyonu
 *
 * Bu yapılandırma ile uygulama bir OAuth2 Resource Server gibi davranır:
 * - Her gelen HTTP isteği, Authorization header'ındaki Bearer JWT token'ını
 * doğrular.
 * - Token doğrulaması, JwtDecoder Bean'i üzerinden yapılır.
 * - JwtDecoder, "spring.security.oauth2.resourceserver.jwt.issuer-uri"
 * değerinden issuer'ın public key'ini otomatik keşfeder (OIDC Discovery).
 *
 * Güvenlik Mimarisi:
 * Client → Bearer JWT → Spring Security Filter → API Controller
 *
 * SessionCreationPolicy.STATELESS:
 * REST API'ler durum taşımaz. JWT token her istekte gönderilir.
 * Session cookie kullanılmaz → CSRF saldırısına karşı ekstra dayanıklılık.
 *
 * Public Endpoint'ler (token gerekmez):
 * - /actuator/health → Docker healthcheck için
 * - /swagger-ui/** → API dokümantasyonu
 * - /api-docs/** → OpenAPI JSON spec
 *
 * Korunan Endpoint'ler (geçerli JWT gerekir):
 * - /api/v1/** → Tüm uçuş ve log API'leri
 *
 * Security Disabled Profile (dev):
 * SECURITY_ENABLED=false env değişkeni ile SecurityConfig tamamen devre dışı
 * bırakılabilir. Bu, local geliştirme sırasında Keycloak çalıştırma
 * zorunluluğunu
 * ortadan kaldırır.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * JWT token güvenliğini etkinleştirip devre dışı bırakabiliriz.
     * Varsayılan: true (prod ortamında daima true olmalı).
     * SECURITY_ENABLED=false → local dev modda tüm endpoint'ler açık.
     */
    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    /**
     * Spring Security HTTP güvenlik filtre zinciri.
     *
     * @param http HttpSecurity builder
     * @return Yapılandırılmış SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Güvenlik devre dışı ise — local dev, test etc.
        if (!securityEnabled) {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        return http
                // CSRF: REST API'lerde stateless JWT kullanılır → CSRF gerek yok
                .csrf(AbstractHttpConfigurer::disable)

                // Session yönetimi: Her istek kendi token'ını taşır → sunucu session tutmaz
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Yetkilendirme kuralları
                .authorizeHttpRequests(auth -> auth
                        // Sağlık kontrolü: Docker healthcheck, monitoring sistemleri erişebilmeli
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Swagger / OpenAPI dokümantasyon arayüzü
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        // Prometheus (ağ seviyesinde kısıtlama yapılmalı, API seviyesinde açık)
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // GET isteklerine kimlik doğrulaması zorunlu
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        // Tüm diğer istekler doğrulanmış olmalı
                        .anyRequest().authenticated())

                // OAuth2 Resource Server: JWT Bearer token doğrulaması
                // application.yml'deki jwt.issuer-uri'den public key otomatik keşfedilir
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {
                            // Ekstra JWT claim doğrulaması gerekirse buraya eklenebilir
                            // Örnek: jwt.jwtAuthenticationConverter(converter)
                        }))
                .build();
    }
}
