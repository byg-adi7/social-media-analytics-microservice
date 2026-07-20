package com.platform.notification.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private final UUID userId;
    private final String email;
    private final String role;
}
