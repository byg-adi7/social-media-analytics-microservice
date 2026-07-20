package com.platform.notification.exception;

import com.platform.notification.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a request cannot be authenticated, e.g. the JWT is missing,
 * malformed, expired, or rejected by the Auth Service, or (for the
 * internal-only endpoints) the internal API key is missing/wrong.
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }
}
