package com.platform.notification.constant;

/**
 * Each value maps to one existing Analytics Service endpoint whose response
 * this service formats as CSV. No speculative report types.
 */
public enum ReportType {
    /** Backed by GET /api/analytics/platform-comparison. */
    PLATFORM_COMPARISON,
    /** Backed by GET /api/analytics/summary. */
    SUMMARY
}
