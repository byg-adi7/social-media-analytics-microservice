package com.platform.analytics.validator;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.exception.PlatformNotSupportedException;
import org.springframework.stereotype.Component;

/**
 * Validates that a platform value is one of the currently supported
 * platforms before it is used in downstream business logic.
 */
@Component
public class PlatformValidator {

    public void validate(Platform platform) {
        if (platform == null) {
            throw new PlatformNotSupportedException("null");
        }
        try {
            Platform.valueOf(platform.name());
        } catch (IllegalArgumentException ex) {
            throw new PlatformNotSupportedException(platform.name());
        }
    }
}
