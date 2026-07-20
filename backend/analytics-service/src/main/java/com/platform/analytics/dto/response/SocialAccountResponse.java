package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response representation of a connected social account.
 * Never includes accessToken/refreshToken for security reasons.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccountResponse {

    private UUID id;
    private Platform platform;
    private String accountId;
    private String accountName;
    private String username;
    private String profileImage;
    private LocalDateTime connectedAt;
    private LocalDateTime lastSynced;
    private boolean active;
}
