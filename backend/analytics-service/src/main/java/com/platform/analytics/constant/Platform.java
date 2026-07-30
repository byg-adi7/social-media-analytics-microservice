package com.platform.analytics.constant;

/**
 * Enumeration of social media platforms supported by the Analytics Service.
 * <p>
 * Designed to be easily extensible: to add a new platform (e.g. X, LinkedIn,
 * Twitch) simply add a new constant here and provide a corresponding
 * implementation in the {@code client} package's mock/real social media client.
 */
public enum Platform {

    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    SPOTIFY("Spotify"),
    /** No real API integration exists for this platform - added for CSV import. */
    TWITTER("Twitter/X");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
