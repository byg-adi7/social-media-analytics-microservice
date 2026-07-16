package com.platform.analytics.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Represents the authenticated principal derived from a validated JWT,
 * as returned by the Auth Service. Stored as the principal in the Spring
 * Security context for the duration of the request.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private final UUID userId;
    private final String email;
    private final String role;
}
