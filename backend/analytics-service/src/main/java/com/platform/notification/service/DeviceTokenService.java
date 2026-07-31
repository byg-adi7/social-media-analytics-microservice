package com.platform.notification.service;

import com.platform.notification.dto.request.RegisterDeviceRequest;
import com.platform.notification.dto.response.DeviceTokenResponse;

import java.util.UUID;

public interface DeviceTokenService {

    DeviceTokenResponse register(UUID userId, RegisterDeviceRequest request);

    void unregister(UUID userId, String token);

    void deleteAllForUser(UUID userId);
}
