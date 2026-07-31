package com.platform.notification.controller;

import com.platform.analytics.security.SecurityContextUtil;
import com.platform.notification.dto.request.RegisterDeviceRequest;
import com.platform.notification.dto.request.UnregisterDeviceRequest;
import com.platform.notification.dto.response.DeviceTokenResponse;
import com.platform.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Registers/deregisters the current user's device push tokens (Firebase
 * Cloud Messaging) so NotificationService.notifyUser can fan push
 * notifications out to them.
 */
@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Register/deregister push-notification device tokens")
public class DeviceController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register (or refresh) this device's FCM push token for the current user",
            description = "Safe to call every app launch - re-registering the same token just refreshes it.")
    @ApiResponse(responseCode = "201", description = "Device token registered")
    public DeviceTokenResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Incoming request: register device, platform={}", request.getPlatform());
        return deviceTokenService.register(userId, request);
    }

    @DeleteMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deregister a device's FCM push token, e.g. on logout")
    @ApiResponse(responseCode = "404", description = "Token not found for the current user")
    public void unregister(@Valid @RequestBody UnregisterDeviceRequest request) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Incoming request: unregister device");
        deviceTokenService.unregister(userId, request.getToken());
    }
}
