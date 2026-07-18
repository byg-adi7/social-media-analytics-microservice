package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from both {@code GET https://graph.instagram.com/access_token}
 * (initial long-lived exchange) and {@code GET .../refresh_access_token}
 * (renewing an existing long-lived token) — both return this same flat
 * shape. {@code expiresIn} is seconds until expiry (long-lived tokens last
 * ~60 days).
 */
public record InstagramLongLivedTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn
) {
}
