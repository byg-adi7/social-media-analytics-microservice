package com.platform.analytics.constant;

/**
 * Mirrors the subset of com.platform.notification.constant.NotificationType
 * that this service ever sends - it doesn't need REPORT_READY, which the
 * Notification Service fires on itself.
 */
public enum NotificationType {
    ACCOUNT_CONNECTED,
    SYNC_FAILURE
}
