package com.audienceinsights.audience_insights_auth_service.Exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends AuthException {
    public InvalidVerificationTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
