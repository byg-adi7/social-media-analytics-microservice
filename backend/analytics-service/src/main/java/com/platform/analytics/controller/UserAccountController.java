package com.platform.analytics.controller;

import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.SupabaseAdminService;
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

    @DeleteMapping("/me")
    @Operation(summary = "Permanently delete the authenticated user's account",
            description = "Deletes the underlying Supabase Auth user, which triggers the existing "
                    + "user-deleted Database Webhook to cascade-delete all app data for this user "
                    + "(connected accounts, analytics, notifications, reports, device tokens). "
                    + "Irreversible.")
    public ResponseEntity<Void> deleteMyAccount() {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Incoming request: delete own account, user={}", userId);
        supabaseAdminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
