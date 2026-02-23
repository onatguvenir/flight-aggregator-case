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
 * - JwtDecoder, "JWT_SECRET" simetrik anahtarını kullanarak HMAC (HS256)
 * ile JWT imzalarını doğrular.
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
 * - /actuator/health → Docker healthcheck için
 * - /actuator/prometheus → Prometheus metrics koleksiyonu
 *
 * Korunan Endpoint'ler (geçerli JWT gerekir):
 * - /api/v1/** → Tüm uçuş ve log API'leri
 *
 * Security Disabled Profile (dev):
 * SECURITY_ENABLED=false env değişkeni ile SecurityConfig tamamen devre dışı
 * bırakılabilir. Bu, local geliştirme sırasında token üretim
 * zorunluluğunu ortadan kaldırır.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.secret-string}")
    private String jwtSecret;

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
                        // Swagger ve OpenAPI dokümantasyon arayüzüne (Uygulama Güvenlik Gereksinimleri)
                        // JWT doğrulaması mecbur kılındı
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**")
                        .authenticated()
                        // GET isteklerine kimlik doğrulaması zorunlu
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        // Tüm diğer istekler doğrulanmış olmalı
                        .anyRequest().authenticated())

                // OAuth2 Resource Server: JWT Bearer token doğrulaması
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))
                .build();
    }

    /**
     * Simetrik anahtar (HS256) ile JWT token çözen decoder bean'i.
     * Güçlü bir secret kullanıldığından (HMAC) emin olur.
     */
    @Bean
    public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
        byte[] secretKeyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(secretKeyBytes, "HmacSHA256");
        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
}
