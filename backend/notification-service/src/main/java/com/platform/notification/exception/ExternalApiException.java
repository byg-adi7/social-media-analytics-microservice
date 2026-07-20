package com.platform.notification.exception;

import com.platform.notification.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a call out to the Analytics Service (to pull real data for a
 * report) fails.
 */
public class ExternalApiException extends ApiException {
    public ExternalApiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_ERROR);
    }
}
