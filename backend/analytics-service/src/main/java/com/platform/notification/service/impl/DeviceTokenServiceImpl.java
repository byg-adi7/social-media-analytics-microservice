package com.platform.notification.service.impl;

import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.request.RegisterDeviceRequest;
import com.platform.notification.dto.response.DeviceTokenResponse;
import com.platform.notification.entity.DeviceToken;
import com.platform.notification.repository.DeviceTokenRepository;
import com.platform.notification.service.DeviceTokenService;
import com.platform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationService notificationService;

    /**
     * token is globally unique, not per-user: registering a token already
     * on file - whether for this same user (app relaunch resending its
     * token) or a different one (a different account signing into the same
     * physical device after a logout) - upserts that one row rather than
     * creating a duplicate or leaving it pointed at the previous owner.
     */
    @Override
    @Transactional
    public DeviceTokenResponse register(UUID userId, RegisterDeviceRequest request) {
        boolean hadAnyDeviceBefore = deviceTokenRepository.existsByUserId(userId);
        Optional<DeviceToken> existing = deviceTokenRepository.findByToken(request.getToken());

        DeviceToken deviceToken;
        boolean isNewAssociationForThisUser;

        if (existing.isPresent()) {
            deviceToken = existing.get();
            isNewAssociationForThisUser = !deviceToken.getUserId().equals(userId);
            deviceToken.setUserId(userId);
            deviceToken.setPlatform(request.getPlatform());
            deviceToken.setActive(true);
            deviceToken.setLastUsedAt(LocalDateTime.now());
        } else {
            deviceToken = DeviceToken.builder()
                    .userId(userId)
                    .token(request.getToken())
                    .platform(request.getPlatform())
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .lastUsedAt(LocalDateTime.now())
                    .build();
            isNewAssociationForThisUser = true;
        }

        DeviceToken saved = deviceTokenRepository.save(deviceToken);
        log.info("Registered {} device token for user {}", request.getPlatform(), userId);

        // Suppressed on the user's very first device (avoids a redundant
        // NEW_DEVICE_LOGIN alongside WELCOME right after signup) - fires for
        // every device registered after that.
        if (isNewAssociationForThisUser && hadAnyDeviceBefore) {
            notificationService.notifyUser(userId, NotificationType.NEW_DEVICE_LOGIN, "New device signed in",
                    "A new " + request.getPlatform() + " device was just registered to your account.",
                    Map.of("deviceTokenId", saved.getId().toString()));
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void unregister(UUID userId, String token) {
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .filter(dt -> dt.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Device token not found for this user"));

        deviceToken.setActive(false);
        deviceTokenRepository.save(deviceToken);
        log.info("Unregistered a device token for user {}", userId);
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        deviceTokenRepository.deleteAllByUserId(userId);
    }

    private DeviceTokenResponse toResponse(DeviceToken deviceToken) {
        return DeviceTokenResponse.builder()
                .id(deviceToken.getId())
                .platform(deviceToken.getPlatform())
                .active(deviceToken.isActive())
                .createdAt(deviceToken.getCreatedAt())
                .lastUsedAt(deviceToken.getLastUsedAt())
                .build();
    }
}
