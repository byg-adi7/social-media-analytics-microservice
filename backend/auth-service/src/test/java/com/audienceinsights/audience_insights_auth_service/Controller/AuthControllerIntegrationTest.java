package com.audienceinsights.audience_insights_auth_service.Controller;

import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Exception.ErrorResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real HTTP requests against the full application context. Since
 * identity is now entirely delegated to Supabase Auth, this service has no
 * register/login of its own to test - only that /validate correctly
 * verifies a token shaped and signed exactly like Supabase issues one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${supabase.jwt-secret}")
    private String supabaseJwtSecret;

    private String supabaseStyleToken(UUID userId, String email, long expiresInMillis) {
        SecretKey key = Keys.hmacShaKeyFor(supabaseJwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", "authenticated")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(key)
                .compact();
    }

    @Test
    void validate_returnsValidTrueWithClaims_forAGenuineSupabaseToken() {
        UUID userId = UUID.randomUUID();
        String token = supabaseStyleToken(userId, "someone@example.com", 60_000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isValid()).isTrue();
        assertThat(response.getBody().getUserId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo("someone@example.com");
        assertThat(response.getBody().getRole()).isEqualTo("authenticated");
    }

    @Test
    void validate_returnsValidFalse_forAnExpiredToken() {
        String token = supabaseStyleToken(UUID.randomUUID(), "expired@example.com", -60_000);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isValid()).isFalse();
    }

    @Test
    void validate_returnsValidFalse_forATokenSignedWithTheWrongSecret() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a-completely-different-secret-not-used-by-this-service".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "forged@example.com")
                .claim("role", "authenticated")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isValid()).isFalse();
    }

    @Test
    void validate_returnsValidFalse_forAGarbageToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");

        ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isValid()).isFalse();
    }

    @Test
    void validate_returnsBadRequest_whenAuthorizationHeaderIsMissing() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, HttpEntity.EMPTY, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
