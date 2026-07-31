package com.platform.analytics.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Verifies JWTs issued by Supabase Auth locally - identity itself (register,
 * login, password hashing, email verification) is fully delegated to
 * Supabase; this service only checks that a token is genuine.
 * <p>
 * This Supabase project migrated from a single legacy HMAC (HS256) shared
 * secret to asymmetric JWT Signing Keys (ES256): new tokens carry a `kid`
 * header identifying which public key - fetched from Supabase's JWKS
 * endpoint - signed them. Tokens with a `kid` are verified against the
 * fetched/cached JWKS public key; tokens without one (issued before the
 * migration) fall back to the legacy shared secret, so both are honored
 * during and after the transition.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${supabase.jwt-secret}")
    private String supabaseJwtSecret;

    @Value("${supabase.url}")
    private String supabaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, ECPublicKey> jwksCache = new ConcurrentHashMap<>();
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile long lastFetchedAt = 0;

    // Matches how long Supabase's own edge/client libraries cache the JWKS response.
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000;

    private final Locator<Key> keyLocator = new LocatorAdapter<Key>() {
        @Override
        public Key locate(JwsHeader header) {
            String kid = header.getKeyId();
            if (kid == null) {
                return getLegacySigningKey();
            }
            return resolveJwksKey(kid);
        }
    };

    private SecretKey getLegacySigningKey() {
        // Supabase's legacy JWT secret is used as-is (its raw UTF-8 bytes), not
        // base64-decoded first - it happens to look like base64 because
        // Supabase generates it as a long random string, but GoTrue (and every
        // documented Supabase JWT-verification example) treats it as a plain
        // secret string.
        return Keys.hmacShaKeyFor(supabaseJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Key resolveJwksKey(String kid) {
        ECPublicKey key = jwksCache.get(kid);
        if (key != null) return key;

        refreshJwks();
        key = jwksCache.get(kid);
        if (key == null) {
            throw new IllegalStateException("No matching Supabase JWKS key found for kid=" + kid);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private void refreshJwks() {
        long now = System.currentTimeMillis();
        if (now - lastFetchedAt < CACHE_TTL_MILLIS && !jwksCache.isEmpty()) return;

        refreshLock.lock();
        try {
            // Re-check after acquiring the lock - another thread may have just refreshed.
            if (now - lastFetchedAt < CACHE_TTL_MILLIS && !jwksCache.isEmpty()) return;

            String jwksUrl = supabaseUrl + "/auth/v1/.well-known/jwks.json";
            Map<String, Object> response = restTemplate.getForObject(jwksUrl, Map.class);
            List<Map<String, String>> keys = response != null
                    ? (List<Map<String, String>>) response.get("keys")
                    : List.of();

            for (Map<String, String> jwk : keys) {
                if (!"EC".equals(jwk.get("kty"))) continue; // Supabase signs with ES256 (EC) keys only
                jwksCache.put(jwk.get("kid"), toEcPublicKey(jwk));
            }
            lastFetchedAt = now;
            log.info("Refreshed Supabase JWKS from {} - {} key(s) cached", jwksUrl, jwksCache.size());
        } catch (Exception e) {
            log.error("Failed to refresh Supabase JWKS from {}: {}", supabaseUrl, e.getMessage());
        } finally {
            refreshLock.unlock();
        }
    }

    private ECPublicKey toEcPublicKey(Map<String, String> jwk) {
        try {
            String curveName = "P-256".equals(jwk.get("crv")) ? "secp256r1" : jwk.get("crv");
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(curveName));
            ECParameterSpec ecParameterSpec = params.getParameterSpec(ECParameterSpec.class);

            Base64.Decoder decoder = Base64.getUrlDecoder();
            BigInteger x = new BigInteger(1, decoder.decode(jwk.get("x")));
            BigInteger y = new BigInteger(1, decoder.decode(jwk.get("y")));

            ECPublicKeySpec pubSpec = new ECPublicKeySpec(new ECPoint(x, y), ecParameterSpec);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(pubSpec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse a Supabase JWKS EC key", e);
        }
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
     * "authenticated"), not an app-level permission role.
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * A token can be cryptographically genuine (correctly signed, not
     * expired) but still carry a malformed `sub` claim - e.g. missing or
     * not a valid UUID. Checking that here, not just signature/expiry,
     * means callers can trust extractUserId() will never throw for
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
                .keyLocator(keyLocator)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
