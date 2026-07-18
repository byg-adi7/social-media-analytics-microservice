package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body returned by Facebook's {@code GET /oauth/access_token}
 * endpoint, used both for the initial authorization-code exchange (short-lived
 * user token) and the {@code fb_exchange_token} call (long-lived user token).
 * Both calls return the same shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresInSeconds
) {
}
