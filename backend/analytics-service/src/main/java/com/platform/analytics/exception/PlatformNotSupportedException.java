package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a client requests an operation for a social media platform
 * that is not yet supported by the service.
 */
public class PlatformNotSupportedException extends ApiException {

    public PlatformNotSupportedException(String platform) {
        super("Platform not supported: " + platform, HttpStatus.BAD_REQUEST, ErrorCode.PLATFORM_NOT_SUPPORTED);
    }
}
