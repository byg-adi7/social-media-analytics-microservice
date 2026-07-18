package com.platform.analytics.tiktok.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code POST /v2/video/list/}. Only the fields the
 * Analytics Service actually needs are mapped, per the official TikTok
 * Video Object reference.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TikTokVideoListResponse(Data data, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Video> videos, Long cursor, @JsonProperty("has_more") Boolean hasMore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(
            String id,
            @JsonProperty("create_time") Long createTime,
            @JsonProperty("cover_image_url") String coverImageUrl,
            @JsonProperty("share_url") String shareUrl,
            @JsonProperty("video_description") String videoDescription,
            String title,
            @JsonProperty("like_count") Long likeCount,
            @JsonProperty("comment_count") Long commentCount,
            @JsonProperty("share_count") Long shareCount,
            @JsonProperty("view_count") Long viewCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String message, @JsonProperty("log_id") String logId) {
    }
}
