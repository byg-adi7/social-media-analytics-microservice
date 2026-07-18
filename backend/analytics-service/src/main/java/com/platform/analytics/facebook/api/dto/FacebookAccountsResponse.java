package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code GET /{user-id}/accounts} — the list of Facebook
 * Pages the connecting person manages, each with its own (non-expiring,
 * when derived from a long-lived user token) Page access token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookAccountsResponse(List<Page> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(String id, String name, String category, @JsonProperty("access_token") String accessToken) {
    }
}
