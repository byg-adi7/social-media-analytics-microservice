package com.platform.notification.dto.response;

/**
 * Mirrors com.platform.analytics.constant.Platform's constant names, purely
 * so Jackson can deserialize the Analytics Service's Feign responses -
 * these two services don't share a library, so this is a deliberate,
 * minimal duplication (same pattern already used for TokenValidationResponse
 * between the Auth and Analytics Services).
 */
public enum AnalyticsPlatform {
    YOUTUBE,
    INSTAGRAM,
    TIKTOK,
    FACEBOOK,
    SPOTIFY
}
