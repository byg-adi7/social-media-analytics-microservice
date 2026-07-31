package com.platform.notification.constant;

/**
 * Every value here corresponds to a real, wired trigger - not a
 * placeholder for hypothetical future events - with one documented
 * exception: SUBSCRIPTION_SUCCESS/SUBSCRIPTION_EXPIRING exist for forward
 * compatibility only, since this app has no subscription/billing system to
 * fire them from (see database/schema.sql's header comment).
 */
public enum NotificationType {
    /** Fired once, right after a new user's first-ever account/device is registered. */
    WELCOME,
    /** Fired by the Analytics Service when a new social account finishes connecting. */
    ACCOUNT_CONNECTED,
    /** Fired when a CSV re-upload finishes merging into an existing account. */
    ANALYSIS_COMPLETED,
    /** Fired after a user-triggered on-demand sync succeeds (not the routine scheduled batch, to avoid daily spam). */
    SYNC_SUCCESS,
    /** Fired by the Analytics Service's scheduled sync job when an account's sync fails. */
    SYNC_FAILURE,
    /** Fired locally when a requested report finishes generating. */
    REPORT_READY,
    /** Fired via the Supabase auth.users UPDATE webhook when the password hash actually changes. */
    PASSWORD_CHANGED,
    /** Fired when a device token is registered that the user hasn't registered before. */
    NEW_DEVICE_LOGIN,
    /** Not currently fired by any code path - no subscription/billing system exists in this app yet. */
    SUBSCRIPTION_SUCCESS,
    /** Not currently fired by any code path - no subscription/billing system exists in this app yet. */
    SUBSCRIPTION_EXPIRING
}
