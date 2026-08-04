package com.platform.analytics.controller;

import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.SupabaseAdminService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service operations on the authenticated user's own account -
 * distinct from {@link AccountController}, which manages the user's
 * connected social media accounts, not their own identity.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Account", description = "Self-service operations on the authenticated user's own account")
public class UserAccountController {

    private final SupabaseAdminService supabaseAdminService;
    private final NotificationService notificationService;

    @DeleteMapping("/me")
    @Operation(summary = "Permanently delete the authenticated user's account",
            description = "Deletes the underlying Supabase Auth user, which triggers the existing "
                    + "user-deleted Database Webhook to cascade-delete all app data for this user "
                    + "(connected accounts, analytics, notifications, reports, device tokens). "
                    + "Irreversible.")
    public ResponseEntity<Void> deleteMyAccount() {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Incoming request: delete own account, user={}", userId);

        // Sent before deleteUser(), not after: the user-deleted webhook that
        // follows cascade-deletes this user's device tokens, so anything
        // sent afterward would have no token left to push to. Best-effort -
        // a notification failure here must not block the actual deletion.
        try {
            notificationService.notifyUser(userId, NotificationType.ACCOUNT_DELETED, "Account deleted",
                    "Your Audience Insights account and all associated data have been permanently deleted.", null);
        } catch (Exception ex) {
            log.warn("Failed to send account-deleted notification for user {}: {}", userId, ex.getMessage());
        }

        supabaseAdminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
