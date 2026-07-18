package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code GET /{page-id}/posts}. Likes/comments are read via
 * the {@code .summary(true)} field-expansion syntax; {@code shares} is a
 * plain count object that Facebook omits entirely from the response when a
 * post has zero shares (hence it being nullable here).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookPostsResponse(List<Post> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Post(
            String id,
            String message,
            @JsonProperty("created_time") String createdTime,
            @JsonProperty("full_picture") String fullPicture,
            @JsonProperty("permalink_url") String permalinkUrl,
            Likes likes,
            Comments comments,
            Shares shares
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Likes(Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Comments(Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(@JsonProperty("total_count") Long totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shares(Long count) {
    }
}
