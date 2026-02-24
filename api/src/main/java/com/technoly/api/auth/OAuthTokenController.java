package com.technoly.api.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * Access Token endpoint for a simple OAuth2 Client Credentials flow.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuthTokenController {

    private final JwtEncoder jwtEncoder;

    @Value("${auth.client-id:flight-client}")
    private String clientId;

    @Value("${auth.client-secret:flight-secret}")
    private String clientSecret;

    @Value("${auth.token-validity-seconds:3600}")
    private long tokenValiditySeconds;

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", defaultValue = "client_credentials") String grantType,
            @RequestParam(value = "client_id", required = false) String formClientId,
            @RequestParam(value = "client_secret", required = false) String formClientSecret,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (!"client_credentials".equals(grantType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "unsupported_grant_type",
                            "error_description", "Only client_credentials grant_type is supported."));
        }

        String[] clientCredsFromHeader = extractClientFromBasicAuth(authorizationHeader);
        String providedClientId = clientCredsFromHeader[0] != null ? clientCredsFromHeader[0] : formClientId;
        String providedClientSecret = clientCredsFromHeader[1] != null ? clientCredsFromHeader[1] : formClientSecret;

        if (!StringUtils.hasText(providedClientId) || !StringUtils.hasText(providedClientSecret)) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "invalid_client",
                            "error_description", "client_id / client_secret is missing."));
        }

        if (!clientId.equals(providedClientId) || !clientSecret.equals(providedClientSecret)) {
            log.warn("Invalid client credentials: {}", providedClientId);
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "invalid_client",
                            "error_description", "client_id or client_secret is incorrect."));
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenValiditySeconds, ChronoUnit.SECONDS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("flight-aggregator-auth")
                .subject(providedClientId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("scope", "flights.read")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        Map<String, Object> responseBody = Map.of(
                "access_token", tokenValue,
                "token_type", "Bearer",
                "expires_in", tokenValiditySeconds);

        return ResponseEntity.ok(responseBody);
    }

    private String[] extractClientFromBasicAuth(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return new String[] { null, null };
        }
        try {
            String base64Credentials = authorizationHeader.substring("Basic ".length());
            byte[] decoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(decoded, StandardCharsets.UTF_8);
            int separatorIndex = credentials.indexOf(':');
            if (separatorIndex == -1) {
                return new String[] { null, null };
            }
            String id = credentials.substring(0, separatorIndex);
            String secret = credentials.substring(separatorIndex + 1);
            return new String[] { id, secret };
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode Basic Authorization header: {}", e.getMessage());
            return new String[] { null, null };
        }
    }
}
