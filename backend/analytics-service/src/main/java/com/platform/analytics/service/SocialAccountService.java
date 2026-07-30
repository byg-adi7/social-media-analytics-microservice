package com.platform.analytics.service;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.CsvImportResponse;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Handles connecting, retrieving, updating, disconnecting and syncing
 * social media accounts on behalf of the current authenticated user.
 */
public interface SocialAccountService {

    SocialAccountResponse connectAccount(UUID userId, ConnectAccountRequest request);

    PagedResponse<SocialAccountResponse> getAccounts(UUID userId, Pageable pageable);

    SocialAccountResponse getAccountById(UUID userId, UUID accountId);

    SocialAccountResponse updateAccount(UUID userId, UUID accountId, UpdateAccountRequest request);

    void disconnectAccount(UUID userId, UUID accountId);

    SocialAccountResponse syncAccount(UUID userId, UUID accountId);

    /** Creates a new CSV_IMPORT account and loads the file's rows as its initial data. */
    CsvImportResponse importCsv(UUID userId, Platform platform, String accountName, MultipartFile file);

    /** Merges (upserts by date) more CSV rows into an existing CSV_IMPORT account. */
    CsvImportResponse mergeCsv(UUID userId, UUID accountId, MultipartFile file);
}
