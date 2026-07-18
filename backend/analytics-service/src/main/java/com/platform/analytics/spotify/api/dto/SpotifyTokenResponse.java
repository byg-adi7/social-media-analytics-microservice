package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body returned by Spotify's Accounts Service token endpoint
 * ({@code POST https://accounts.spotify.com/api/token}), both for the
 * initial authorization-code exchange and for refresh-token requests.
 * Spotify usually — but not always — returns a fresh {@code refresh_token}
 * on a refresh grant, so callers should keep the previous one when this
 * field is absent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresInSeconds,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType
) {
}
