package com.platform.analytics.controller;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.CsvImportResponse;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.SocialAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Endpoints for connecting, retrieving, updating, disconnecting and
 * on-demand syncing of social media accounts.
 */
@Slf4j
@Validated
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
    @Operation(summary = "List all connected accounts for the current user, most recently connected first",
            description = "Paginated: pass page/size query params (defaults: page=0, size=20).")
    public PagedResponse<SocialAccountResponse> getAccounts(
            @PageableDefault(size = 20, sort = "connectedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Incoming request: list accounts");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.getAccounts(userId, pageable);
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

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account from an uploaded CSV of daily metrics",
            description = "For platforms without a live connection (e.g. Twitter/X), or as a supplementary "
                    + "manual data source alongside an already-connected account for the same platform. "
                    + "CSV columns (any order): date,followers,views,likes,comments,shares.")
    public CsvImportResponse importCsv(
            @RequestParam Platform platform,
            @RequestParam @NotBlank(message = "accountName must not be blank") String accountName,
            @RequestPart("file") MultipartFile file) {
        log.info("Incoming request: import CSV, platform={}", platform);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.importCsv(userId, platform, accountName, file);
    }

    @PostMapping(value = "/{id}/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Re-upload a CSV into an existing CSV-imported account",
            description = "Upserts by date: rows for dates already present are updated, new dates are added.")
    public CsvImportResponse reimportCsv(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        log.info("Incoming request: merge CSV into account {}", id);
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return socialAccountService.mergeCsv(userId, id, file);
    }
}
