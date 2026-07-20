package com.platform.analytics.tiktok.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for {@code GET /v2/user/info/}. Only the fields the
 * Analytics Service actually needs are mapped; both records are tolerant of
 * unknown properties since the real endpoint returns additional fields
 * this service doesn't request. {@code username}, {@code follower_count},
 * {@code following_count}, {@code likes_count}, and {@code video_count}
 * each require their own OAuth scope ({@code user.info.profile} /
 * {@code user.info.stats}) to be populated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TikTokUserInfoResponse(Data data, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(User user) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            @JsonProperty("open_id") String openId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("username") String username,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("follower_count") Long followerCount,
            @JsonProperty("following_count") Long followingCount,
            @JsonProperty("likes_count") Long likesCount,
            @JsonProperty("video_count") Long videoCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String code, String message, @JsonProperty("log_id") String logId) {
    }
}
