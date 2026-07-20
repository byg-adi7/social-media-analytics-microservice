package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code GET https://graph.instagram.com/v25.0/me}, the IG
 * User node. Field names verified against Meta's IG User reference and the
 * Instagram API with Instagram Login "Get Started" guide (which uses
 * {@code user_id} as the identifier field for this login flow).
 */
public record InstagramProfileResponse(
        @JsonProperty("user_id") String userId,
        String username,
        String name,
        String biography,
        @JsonProperty("followers_count") Long followersCount,
        @JsonProperty("follows_count") Long followsCount,
        @JsonProperty("media_count") Long mediaCount,
        @JsonProperty("profile_picture_url") String profilePictureUrl
) {
}
