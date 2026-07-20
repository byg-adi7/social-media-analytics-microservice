package com.audienceinsights.audience_insights_auth_service.Exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends AuthException {
    public EmailAlreadyRegisteredException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
