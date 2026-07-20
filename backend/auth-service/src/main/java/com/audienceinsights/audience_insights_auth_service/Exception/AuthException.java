package com.audienceinsights.audience_insights_auth_service.Exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for Auth Service domain errors. Carries an HTTP
 * status so {@link GlobalExceptionHandler} can translate it into a proper
 * JSON error response instead of an uncaught-exception stack trace.
 */
public abstract class AuthException extends RuntimeException {

    private final HttpStatus status;

    protected AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
