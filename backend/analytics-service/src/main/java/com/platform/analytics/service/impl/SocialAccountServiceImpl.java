package com.platform.analytics.service.impl;

import com.platform.analytics.client.NotificationServiceClient;
import com.platform.analytics.config.InternalApiProperties;
import com.platform.analytics.constant.NotificationType;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.CreateNotificationRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.service.SocialAccountService;
import com.platform.analytics.validator.PlatformValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAccountServiceImpl implements SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final PlatformValidator platformValidator;
    private final AnalyticsSyncService analyticsSyncService;
    private final NotificationServiceClient notificationServiceClient;
    private final InternalApiProperties internalApiProperties;

    @Override
    @Transactional
    public SocialAccountResponse connectAccount(UUID userId, ConnectAccountRequest request) {
        platformValidator.validate(request.getPlatform());

        boolean alreadyConnected = socialAccountRepository.existsByUserIdAndPlatformAndAccountId(
                userId, request.getPlatform(), request.getAccountId());

        if (alreadyConnected) {
            throw new BadRequestException(
                    "This " + request.getPlatform().getDisplayName() + " account is already connected");
        }

        SocialAccount account = SocialAccount.builder()
                .userId(userId)
                .platform(request.getPlatform())
                .accountId(request.getAccountId())
                .accountName(request.getAccountName())
                .username(request.getUsername())
                .profileImage(request.getProfileImage())
                .accessToken(request.getAccessToken())
                .refreshToken(request.getRefreshToken())
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected new {} account {} for user {}", saved.getPlatform(), saved.getId(), userId);

        // Perform an initial sync so the dashboard has data immediately.
        analyticsSyncService.syncAccount(saved);

        notifyAccountConnected(saved);

        return socialAccountMapper.toResponse(saved);
    }

    /**
     * Best-effort: a Notification Service outage must never fail an
     * otherwise-successful account connection, so this is deliberately
     * swallowed rather than allowed to propagate.
     */
    private void notifyAccountConnected(SocialAccount account) {
        try {
            notificationServiceClient.createNotification(
                    internalApiProperties.getKey(),
                    new CreateNotificationRequest(
                            account.getUserId(),
                            NotificationType.ACCOUNT_CONNECTED,
                            "Your " + account.getPlatform().getDisplayName() + " account was connected successfully."));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    @Override
    public List<SocialAccountResponse> getAccounts(UUID userId) {
        return socialAccountMapper.toResponseList(socialAccountRepository.findAllByUserId(userId));
    }

    @Override
    public SocialAccountResponse getAccountById(UUID userId, UUID accountId) {
        return socialAccountMapper.toResponse(findAccountOrThrow(userId, accountId));
    }

    @Override
    @Transactional
    public SocialAccountResponse updateAccount(UUID userId, UUID accountId, UpdateAccountRequest request) {
        SocialAccount account = findAccountOrThrow(userId, accountId);

        if (request.getAccountName() != null) {
            account.setAccountName(request.getAccountName());
        }
        if (request.getUsername() != null) {
            account.setUsername(request.getUsername());
        }
        if (request.getProfileImage() != null) {
            account.setProfileImage(request.getProfileImage());
        }
        if (request.getActive() != null) {
            account.setActive(request.getActive());
        }

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Updated account {} for user {}", accountId, userId);
        return socialAccountMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void disconnectAccount(UUID userId, UUID accountId) {
        SocialAccount account = findAccountOrThrow(userId, accountId);
        socialAccountRepository.delete(account);
        log.info("Disconnected account {} for user {}", accountId, userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse syncAccount(UUID userId, UUID accountId) {
        SocialAccount account = findAccountOrThrow(userId, accountId);
        analyticsSyncService.syncAccount(account);
        return socialAccountMapper.toResponse(account);
    }

    private SocialAccount findAccountOrThrow(UUID userId, UUID accountId) {
        return socialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("SocialAccount", accountId));
    }
}
