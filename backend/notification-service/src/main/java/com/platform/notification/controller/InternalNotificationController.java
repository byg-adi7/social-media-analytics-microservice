package com.platform.notification.controller;

import com.platform.notification.config.InternalApiProperties;
import com.platform.notification.dto.request.CreateNotificationRequest;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.exception.UnauthorizedException;
import com.platform.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by other backend services, not end users - there is no end-user
 * JWT available when e.g. a scheduled sync job in the Analytics Service
 * needs to notify a user of a failure, so this is guarded by a shared
 * secret header instead of the normal Auth Service JWT delegation. Left out
 * of the public gateway's nginx routes on top of this, so it's only
 * reachable from inside the private Docker network in the first place.
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final NotificationService notificationService;
    private final InternalApiProperties internalApiProperties;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(
            @RequestHeader(API_KEY_HEADER) String apiKey,
            @Valid @RequestBody CreateNotificationRequest request) {

        if (internalApiProperties.getKey() == null || !internalApiProperties.getKey().equals(apiKey)) {
            throw new UnauthorizedException("Invalid or missing internal API key");
        }

        return notificationService.create(request.getUserId(), request.getType(), request.getMessage());
    }
}
