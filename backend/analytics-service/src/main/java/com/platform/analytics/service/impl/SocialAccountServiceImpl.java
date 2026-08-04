package com.platform.analytics.service.impl;

import com.platform.analytics.constant.AccountConnectionType;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.request.UpdateAccountRequest;
import com.platform.analytics.dto.response.CsvImportResponse;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.service.SocialAccountService;
import com.platform.analytics.util.AnalyticsCalculator;
import com.platform.analytics.util.CsvAnalyticsParser;
import com.platform.analytics.validator.PlatformValidator;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAccountServiceImpl implements SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final PlatformValidator platformValidator;
    private final AnalyticsSyncService analyticsSyncService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public SocialAccountResponse connectAccount(UUID userId, ConnectAccountRequest request) {
        platformValidator.validate(request.getPlatform());

        // Matches uk_platform_account_id's actual scope (platform +
        // account_id, not per-user) - a per-user-scoped check here would
        // miss the case where a *different* user already connected this
        // same external account, and the insert below would fail with a
        // raw unique-constraint violation instead of this clean error.
        boolean alreadyConnected = socialAccountRepository.existsByPlatformAndAccountId(
                request.getPlatform(), request.getAccountId());

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

        // Flushed immediately, not deferred to commit - see
        // YouTubeConnectionServiceImpl.completeConnection for why: a
        // constraint violation surfacing only at commit time would happen
        // after syncAccount()/notifyAccountConnected() below already ran as
        // irreversible side effects.
        SocialAccount saved = socialAccountRepository.saveAndFlush(account);
        log.info("Connected new {} account {} for user {}", saved.getPlatform(), saved.getId(), userId);

        // Perform an initial sync so the dashboard has data immediately.
        analyticsSyncService.syncAccount(saved);

        notifyAccountConnected(saved);

        return socialAccountMapper.toResponse(saved);
    }

    /**
     * Best-effort: a failure creating the notification must never fail an
     * otherwise-successful account connection, so this is deliberately
     * swallowed rather than allowed to propagate.
     */
    private void notifyAccountConnected(SocialAccount account) {
        try {
            notificationService.notifyUser(
                    account.getUserId(),
                    NotificationType.ACCOUNT_CONNECTED,
                    "Account connected",
                    "Your " + account.getPlatform().getDisplayName() + " account was connected successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    @Override
    public PagedResponse<SocialAccountResponse> getAccounts(UUID userId, Pageable pageable) {
        return PagedResponse.of(socialAccountRepository.findAllByUserId(userId, pageable).map(socialAccountMapper::toResponse));
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
        // Analytics rows hold a NOT NULL FK to this account - must go first or
        // the delete below fails with a foreign key violation.
        analyticsRepository.deleteBySocialAccountId(accountId);
        socialAccountRepository.delete(account);
        log.info("Disconnected account {} for user {}", accountId, userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse syncAccount(UUID userId, UUID accountId) {
        SocialAccount account = findAccountOrThrow(userId, accountId);
        if (account.getConnectionType() == AccountConnectionType.CSV_IMPORT) {
            throw new BadRequestException(
                    "This account's data comes from a CSV upload, not a live sync - upload a new CSV to update it");
        }
        analyticsSyncService.syncAccount(account);

        // Only for this user-triggered on-demand sync, never the routine
        // scheduled batch (AnalyticsSyncServiceImpl.syncAllActiveAccounts) -
        // that runs hourly for every active account, and a notification on
        // every routine success would be daily spam, not useful signal.
        notifySyncSuccess(account);

        return socialAccountMapper.toResponse(account);
    }

    /**
     * Best-effort, same reasoning as notifyAccountConnected: a failure
     * creating the notification must never fail an otherwise-successful sync.
     */
    private void notifySyncSuccess(SocialAccount account) {
        try {
            notificationService.notifyUser(
                    account.getUserId(),
                    NotificationType.SYNC_SUCCESS,
                    "Sync complete",
                    "Your " + account.getPlatform().getDisplayName() + " account was synced successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send sync-success notification for account {}: {}", account.getId(), ex.getMessage());
        }
    }

    private SocialAccount findAccountOrThrow(UUID userId, UUID accountId) {
        return socialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("SocialAccount", accountId));
    }

    @Override
    @Transactional
    public CsvImportResponse importCsv(UUID userId, Platform platform, String accountName, MultipartFile file) {
        platformValidator.validate(platform);
        List<CsvAnalyticsParser.Row> rows = CsvAnalyticsParser.parse(file);

        // Independent of any OAuth-connected account for the same platform -
        // a synthetic id keeps it out of uk_platform_account_id's way, since
        // that constraint is global (platform + account_id), not per-user.
        SocialAccount account = SocialAccount.builder()
                .userId(userId)
                .platform(platform)
                .accountId("csv-" + UUID.randomUUID())
                .accountName(accountName)
                .connectionType(AccountConnectionType.CSV_IMPORT)
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();
        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Created CSV-imported {} account {} for user {}", saved.getPlatform(), saved.getId(), userId);

        UpsertCounts counts = upsertAnalyticsRows(saved, rows);
        saved.setLastSynced(LocalDateTime.now());
        socialAccountRepository.save(saved);

        notifyAccountConnected(saved);

        return CsvImportResponse.builder()
                .account(socialAccountMapper.toResponse(saved))
                .rowsInserted(counts.inserted())
                .rowsUpdated(counts.updated())
                .build();
    }

    @Override
    @Transactional
    public CsvImportResponse mergeCsv(UUID userId, UUID accountId, MultipartFile file) {
        SocialAccount account = findAccountOrThrow(userId, accountId);
        if (account.getConnectionType() != AccountConnectionType.CSV_IMPORT) {
            throw new BadRequestException("Only CSV-imported accounts can receive additional CSV uploads");
        }

        List<CsvAnalyticsParser.Row> rows = CsvAnalyticsParser.parse(file);
        UpsertCounts counts = upsertAnalyticsRows(account, rows);
        account.setLastSynced(LocalDateTime.now());
        socialAccountRepository.save(account);
        log.info("Merged {} CSV row(s) ({} new, {} updated) into account {} for user {}",
                rows.size(), counts.inserted(), counts.updated(), accountId, userId);

        notifyAnalysisCompleted(account, counts);

        return CsvImportResponse.builder()
                .account(socialAccountMapper.toResponse(account))
                .rowsInserted(counts.inserted())
                .rowsUpdated(counts.updated())
                .build();
    }

    /**
     * Best-effort, same reasoning as notifyAccountConnected: a failure
     * creating the notification must never fail an otherwise-successful merge.
     */
    private void notifyAnalysisCompleted(SocialAccount account, UpsertCounts counts) {
        try {
            notificationService.notifyUser(
                    account.getUserId(),
                    NotificationType.ANALYSIS_COMPLETED,
                    "Analysis ready",
                    "Your uploaded " + account.getPlatform().getDisplayName() + " data has been analyzed ("
                            + counts.inserted() + " new, " + counts.updated() + " updated day(s)).",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send analysis-completed notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    private record UpsertCounts(int inserted, int updated) {
    }

    /** Upserts by (account, date): a re-uploaded CSV updates overlapping dates instead of duplicating them. */
    private UpsertCounts upsertAnalyticsRows(SocialAccount account, List<CsvAnalyticsParser.Row> rows) {
        int inserted = 0;
        int updated = 0;
        for (CsvAnalyticsParser.Row row : rows) {
            Optional<Analytics> existing =
                    analyticsRepository.findBySocialAccountIdAndAnalyticsDate(account.getId(), row.date());
            Analytics analytics = existing.orElseGet(() -> Analytics.builder()
                    .socialAccount(account)
                    .analyticsDate(row.date())
                    .build());

            analytics.setFollowers(row.followers());
            analytics.setViews(row.views());
            analytics.setLikes(row.likes());
            analytics.setComments(row.comments());
            analytics.setShares(row.shares());
            analytics.setEngagementRate(
                    AnalyticsCalculator.engagementRate(row.likes(), row.comments(), row.shares(), row.followers()));

            analyticsRepository.save(analytics);
            if (existing.isPresent()) {
                updated++;
            } else {
                inserted++;
            }
        }
        return new UpsertCounts(inserted, updated);
    }
}
