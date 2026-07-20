package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from {@code POST https://api.instagram.com/oauth/access_token}.
 * Unlike the long-lived token endpoints, this wraps its result in a
 * {@code data} array rather than returning a flat object — verified
 * directly against Meta's Business Login for Instagram documentation.
 */
public record InstagramShortLivedTokenResponse(
        List<Entry> data
) {
    public record Entry(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("user_id") String userId,
            String permissions
    ) {
    }
}
