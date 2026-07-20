package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for {@code GET /{page-id}} — the Page node's own identity
 * fields. {@code followersCount} is preferred over {@code fanCount} for
 * audience size: per Meta's docs, Pages migrated to the "New Page
 * Experience" have {@code fan_count} silently return the same value as
 * {@code followers_count}, so {@code followers_count} is the
 * forward-compatible field to read directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookPageResponse(
        String id,
        String name,
        String category,
        @JsonProperty("followers_count") Long followersCount,
        @JsonProperty("fan_count") Long fanCount,
        Picture picture
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Picture(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String url) {
    }
}
