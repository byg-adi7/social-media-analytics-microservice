package com.platform.analytics.service;

import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.List;
import java.util.UUID;

/**
 * Handles connecting, retrieving, updating, disconnecting and syncing
 * social media accounts on behalf of the current authenticated user.
 */
public interface SocialAccountService {

    SocialAccountResponse connectAccount(UUID userId, ConnectAccountRequest request);

    List<SocialAccountResponse> getAccounts(UUID userId);

    SocialAccountResponse getAccountById(UUID userId, UUID accountId);

    SocialAccountResponse updateAccount(UUID userId, UUID accountId, UpdateAccountRequest request);

    void disconnectAccount(UUID userId, UUID accountId);

    SocialAccountResponse syncAccount(UUID userId, UUID accountId);
}
