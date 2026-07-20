package com.platform.analytics.exception;

import com.platform.analytics.constant.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a call to a real external platform API (e.g. YouTube Data
 * API, Google OAuth token endpoint) fails — network error, non-2xx
 * response, or an unparseable payload. Kept distinct from
 * {@link AnalyticsException} so clients can tell "our calculation broke"
 * apart from "the upstream platform rejected/failed the call".
 */
public class ExternalApiException extends ApiException {

    public ExternalApiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_ERROR);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, ErrorCode.EXTERNAL_API_ERROR);
        initCause(cause);
    }
}
