package com.platform.analytics.security;

import com.platform.analytics.exception.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility for retrieving the current authenticated user's information from
 * the Spring Security context, populated by {@link JwtAuthenticationFilter}.
 */
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
