package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a request cannot be authenticated, e.g. the JWT is missing,
 * malformed, expired, or rejected by the Auth Service.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }
}
