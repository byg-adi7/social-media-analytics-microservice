package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the client sends a malformed or semantically invalid request.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST);
    }
}
