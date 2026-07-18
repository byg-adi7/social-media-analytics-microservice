package com.platform.analytics.tiktok.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body returned by TikTok's OAuth 2.0 token endpoint
 * ({@code POST /v2/oauth/token/}), both for the initial authorization-code
 * exchange and for refresh-token requests. Unlike Google, TikTok returns a
 * fresh {@code refresh_token} on every call (including refreshes), so this
 * one should always be re-stored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TikTokTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresInSeconds,
        @JsonProperty("open_id") String openId,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_expires_in") Long refreshExpiresInSeconds,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType
) {
}
