package com.platform.notification.security;

import com.platform.notification.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static AuthenticatedUser getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("No authenticated user found in security context");
        }
        return user;
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().getUserId();
    }
}
