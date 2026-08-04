package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with an existing resource.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.CONFLICT);
    }
}
