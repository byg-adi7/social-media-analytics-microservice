package com.platform.analytics.controller;

import com.platform.analytics.config.WebhookProperties;
import com.platform.analytics.dto.request.SupabaseWebhookPayload;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.UnauthorizedException;
import com.platform.analytics.service.UserDataCleanupService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Receives Supabase Database Webhook calls on the auth.users table. These
 * endpoints must stay in {@link com.platform.analytics.config.SecurityConfig}'s
 * public allow-list since Supabase, not one of our own users, calls them
 * directly — authenticated by a shared secret header instead of a user JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";

    private final WebhookProperties webhookProperties;
    private final UserDataCleanupService userDataCleanupService;
    private final NotificationService notificationService;

    /** Configure as: Database > Webhooks > table auth.users > event Insert. */
    @PostMapping("/user-created")
    public ResponseEntity<Void> userCreated(
            @RequestHeader(SECRET_HEADER) String providedSecret,
            @RequestBody SupabaseWebhookPayload payload) {
        verifySecret(providedSecret);

        UUID userId = requireId(payload.getRecord(), "record.id");
        log.info("Received user-created webhook for user {}", userId);
        notificationService.notifyUser(userId, NotificationType.WELCOME, "Welcome to Audience Insights",
                "Connect a social account or upload a CSV to see your first analytics.", null);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Configure as: Database > Webhooks > table auth.users > event Update.
     * auth.users UPDATE fires for many unrelated field changes (last sign-in
     * time, metadata, etc.) - only encrypted_password actually changing
     * means the user's password changed, so every other UPDATE is a no-op
     * here. The hash values themselves are only ever compared, never logged
     * or persisted.
     */
    @PostMapping("/user-updated")
    public ResponseEntity<Void> userUpdated(
            @RequestHeader(SECRET_HEADER) String providedSecret,
            @RequestBody SupabaseWebhookPayload payload) {
        verifySecret(providedSecret);

        UUID userId = requireId(payload.getRecord(), "record.id");

        Object newHash = payload.getRecord() != null ? payload.getRecord().get("encrypted_password") : null;
        Object oldHash = payload.getOldRecord() != null ? payload.getOldRecord().get("encrypted_password") : null;

        if (newHash != null && oldHash != null && !Objects.equals(newHash, oldHash)) {
            log.info("Received user-updated webhook for user {}: password changed", userId);
            notificationService.notifyUser(userId, NotificationType.PASSWORD_CHANGED, "Password changed",
                    "Your password was just changed. If this wasn't you, reset it immediately.", null);
        } else {
            log.debug("Received user-updated webhook for user {}: no password change, ignoring", userId);
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Configure as: Database > Webhooks > table auth.users > event Delete. */
    @PostMapping("/user-deleted")
    public ResponseEntity<Void> userDeleted(
            @RequestHeader(SECRET_HEADER) String providedSecret,
            @RequestBody SupabaseWebhookPayload payload) {
        verifySecret(providedSecret);

        UUID userId = requireId(payload.getOldRecord(), "old_record.id");
        log.info("Received user-deleted webhook for user {}", userId);
        userDataCleanupService.deleteAllDataForUser(userId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void verifySecret(String providedSecret) {
        if (!constantTimeEquals(providedSecret, webhookProperties.getSupabaseSecret())) {
            throw new UnauthorizedException("Invalid webhook secret");
        }
    }

    private UUID requireId(Map<String, Object> record, String fieldPath) {
        if (record == null || record.get("id") == null) {
            throw new BadRequestException("Webhook payload is missing " + fieldPath);
        }
        return UUID.fromString(record.get("id").toString());
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
