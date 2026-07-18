package com.platform.analytics.tiktok.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /v2/video/list/}. Both fields are optional
 * per TikTok's docs; {@code cursor} is omitted (null) on the first page.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TikTokVideoListRequest(Long cursor, @JsonProperty("max_count") Integer maxCount) {
}
