package com.platform.notification.constant;

/**
 * Every value here corresponds to a real, wired trigger - not a
 * placeholder for hypothetical future events.
 */
public enum NotificationType {
    /** Fired by the Analytics Service when a new social account finishes connecting. */
    ACCOUNT_CONNECTED,
    /** Fired by the Analytics Service's scheduled sync job when an account's sync fails. */
    SYNC_FAILURE,
    /** Fired locally when a requested report finishes generating. */
    REPORT_READY
}
