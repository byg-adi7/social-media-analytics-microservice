package com.platform.analytics.youtube.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body returned by Google's OAuth 2.0 token endpoint, both for the
 * initial authorization-code exchange and for refresh-token requests. Note
 * that a refresh response typically omits {@code refreshToken}, since
 * Google only returns it once, at initial authorization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresInSeconds,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType
) {
}
