package com.platform.analytics.controller;

import com.platform.analytics.config.WebhookProperties;
import com.platform.analytics.dto.request.SupabaseWebhookPayload;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.UnauthorizedException;
import com.platform.analytics.service.UserDataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Receives Supabase Database Webhook calls. This endpoint must stay in
 * {@link com.platform.analytics.config.SecurityConfig}'s public allow-list
 * since Supabase, not one of our own users, calls it directly — it is
 * instead authenticated by a shared secret header, not a user JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";

    private final WebhookProperties webhookProperties;
    private final UserDataCleanupService userDataCleanupService;

    @PostMapping("/user-deleted")
    public ResponseEntity<Void> userDeleted(
            @RequestHeader(SECRET_HEADER) String providedSecret,
            @RequestBody SupabaseWebhookPayload payload) {

        if (!constantTimeEquals(providedSecret, webhookProperties.getUserDeletionSecret())) {
            throw new UnauthorizedException("Invalid webhook secret");
        }

        if (payload.getOldRecord() == null || payload.getOldRecord().get("id") == null) {
            throw new BadRequestException("Webhook payload is missing old_record.id");
        }

        UUID userId = UUID.fromString(payload.getOldRecord().get("id").toString());
        log.info("Received user-deleted webhook for user {}", userId);
        userDataCleanupService.deleteAllDataForUser(userId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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
