package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when analytics calculation, aggregation, or synchronization fails
 * for reasons that are internal to the service (e.g. corrupt data, mock
 * client failure).
 */
public class AnalyticsException extends ApiException {

    public AnalyticsException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ANALYTICS_ERROR);
    }

    public AnalyticsException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ANALYTICS_ERROR);
        initCause(cause);
    }
}
