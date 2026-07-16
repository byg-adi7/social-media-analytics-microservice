package com.platform.analytics.controller;

import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.SocialAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for connecting, retrieving, updating, disconnecting and
 * on-demand syncing of social media accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Manage connected social media accounts")
public class AccountController {

    private final SocialAccountService socialAccountService;

    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Connect a new social media account",
            description = "Links a YouTube, Instagram, TikTok or Facebook account to the current user and performs an initial sync.")
    @ApiResponse(responseCode = "201", description = "Account connected successfully")
    @ApiResponse(responseCode = "400", description = "Account already connected or invalid platform")
    public SocialAccountResponse connectAccount(@Valid @RequestBody ConnectAccountRequest request) {
        log.info("Incoming request: connect account, platform={}", request.getPlatform());
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.connectAccount(userId, request);
    }

    @GetMapping
    @Operation(summary = "List all connected accounts for the current user")
    public List<SocialAccountResponse> getAccounts() {
        log.info("Incoming request: list accounts");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.getAccounts(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single connected account by id")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public SocialAccountResponse getAccountById(@PathVariable UUID id) {
        log.info("Incoming request: get account {}", id);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.getAccountById(userId, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a connected account's mutable fields")
    public SocialAccountResponse updateAccount(@PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
        log.info("Incoming request: update account {}", id);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.updateAccount(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect a social media account")
    public void disconnectAccount(@PathVariable UUID id) {
        log.info("Incoming request: disconnect account {}", id);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        socialAccountService.disconnectAccount(userId, id);
    }

    @GetMapping("/{id}/sync")
    @Operation(summary = "Trigger an on-demand sync for a single account")
    public SocialAccountResponse syncAccount(@PathVariable UUID id) {
        log.info("Incoming request: sync account {}", id);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.syncAccount(userId, id);
    }
}
