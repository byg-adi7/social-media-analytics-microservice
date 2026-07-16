package com.platform.analytics.security;

import com.platform.analytics.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.platform.analytics.config.YouTubeProperties;

/**
 * Signs and verifies the OAuth {@code state} query parameter used in the
 * YouTube-connect flow.
 * <p>
 * The OAuth callback endpoint is necessarily public (Google redirects the
 * user's raw browser there — it cannot carry our {@code Authorization}
 * header). To still know <em>which of our users</em> is completing the
 * flow, we HMAC-sign {@code userId:issuedAt:nonce} when generating the
 * authorization URL (while the user is authenticated) and verify that
 * signature — plus a short expiry window — on callback. This avoids
 * needing a server-side session store for a single-instance university
 * project while still being tamper-resistant.
 */
@Component
@RequiredArgsConstructor
public class StateTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long STATE_TTL_SECONDS = 600; // 10 minutes

    private final YouTubeProperties youTubeProperties;

    public String generateState(UUID userId) {
        long issuedAt = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        String payload = userId + ":" + issuedAt + ":" + nonce;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public UUID verifyAndExtractUserId(String state) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                throw new UnauthorizedException("Malformed OAuth state parameter");
            }

            String userId = parts[0];
            String issuedAt = parts[1];
            String nonce = parts[2];
            String signature = parts[3];

            String payload = userId + ":" + issuedAt + ":" + nonce;
            String expectedSignature = sign(payload);

            if (!constantTimeEquals(signature, expectedSignature)) {
                throw new UnauthorizedException("OAuth state signature verification failed");
            }

            long issuedAtEpoch = Long.parseLong(issuedAt);
            if (Instant.now().getEpochSecond() - issuedAtEpoch > STATE_TTL_SECONDS) {
                throw new UnauthorizedException("OAuth state parameter has expired — please reconnect");
            }

            return UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid OAuth state parameter");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    youTubeProperties.getStateSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign OAuth state parameter", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
