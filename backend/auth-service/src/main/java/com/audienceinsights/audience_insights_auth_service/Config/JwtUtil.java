package com.audienceinsights.audience_insights_auth_service.Config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Validates JWTs issued by Supabase Auth (not this service - identity is
 * fully delegated to Supabase now). The frontend authenticates directly
 * against Supabase and sends its access token to every other service here;
 * this class is the one place that verifies that token is genuine, so
 * Analytics/Notification never need to know Supabase exists.
 */
@Component
public class JwtUtil {

    @Value("${supabase.jwt-secret}")
    private String supabaseJwtSecret;

    private SecretKey getSigningKey() {
        // Supabase's JWT secret is used as-is (its raw UTF-8 bytes), not
        // base64-decoded first - it happens to look like base64 because
        // Supabase generates it as a long random string, but GoTrue (and
        // every documented Supabase JWT-verification example) treats it as
        // a plain secret string.
        return Keys.hmacShaKeyFor(supabaseJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /** Supabase's `sub` claim is the user's UUID. */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Supabase's `role` claim is a Postgres role marker (normally
     * "authenticated"), not an app-level permission role - this project has
     * no admin/permission system built on top of it yet.
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * A token can be cryptographically genuine (correctly signed, not
     * expired) but still carry a malformed `sub` claim - e.g. missing or
     * not a valid UUID. Checking that here, not just signature/expiry,
     * means callers can trust extractUserId() below will never throw for
     * anything this method already reported as valid.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            UUID.fromString(claims.getSubject());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
