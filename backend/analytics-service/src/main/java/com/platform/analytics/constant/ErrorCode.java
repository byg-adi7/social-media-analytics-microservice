package com.platform.analytics.constant;

/**
 * Centralized error codes returned in API error responses.
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String PLATFORM_NOT_SUPPORTED = "PLATFORM_NOT_SUPPORTED";
    public static final String ANALYTICS_ERROR = "ANALYTICS_ERROR";
    public static final String EXTERNAL_API_ERROR = "EXTERNAL_API_ERROR";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}
