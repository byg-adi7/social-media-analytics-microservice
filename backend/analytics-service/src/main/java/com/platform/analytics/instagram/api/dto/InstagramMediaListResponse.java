package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from {@code GET https://graph.instagram.com/v25.0/{ig-user-id}/media}.
 * Field names verified against Meta's IG Media reference.
 */
public record InstagramMediaListResponse(
        List<Item> data
) {
    public record Item(
            String id,
            String caption,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("media_url") String mediaUrl,
            String permalink,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            String timestamp,
            @JsonProperty("like_count") Long likeCount,
            @JsonProperty("comments_count") Long commentsCount,
            @JsonProperty("view_count") Long viewCount,
            @JsonProperty("shares_count") Long sharesCount
    ) {
    }
}
