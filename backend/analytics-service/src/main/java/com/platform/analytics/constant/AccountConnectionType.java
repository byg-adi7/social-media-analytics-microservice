package com.platform.analytics.constant;

/**
 * How a {@link com.platform.analytics.entity.SocialAccount} got its data.
 * A user may have both for the same platform at once (e.g. a live-synced
 * YouTube account and a separately CSV-imported YouTube account) - these
 * are independent accounts, not alternatives to each other.
 */
public enum AccountConnectionType {
    /** Connected via the platform's own OAuth flow (or MockSocialMediaClient); synced automatically. */
    OAUTH,
    /** Created from a user-uploaded CSV of daily metrics; never auto-synced. */
    CSV_IMPORT
}
