package com.platform.notification.dto.response;

import com.platform.notification.constant.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenResponse {
    private UUID id;
    private DevicePlatform platform;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
