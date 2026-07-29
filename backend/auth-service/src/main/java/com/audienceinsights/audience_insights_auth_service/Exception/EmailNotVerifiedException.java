package com.audienceinsights.audience_insights_auth_service.Exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends AuthException {
    public EmailNotVerifiedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
