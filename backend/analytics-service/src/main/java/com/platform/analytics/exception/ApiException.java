package com.platform.analytics.exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for all Analytics Service domain exceptions.
 * Carries an HTTP status and a machine-readable error code so that
 * {@link com.platform.analytics.exception.GlobalExceptionHandler} can
 * translate it into a consistent error response.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
