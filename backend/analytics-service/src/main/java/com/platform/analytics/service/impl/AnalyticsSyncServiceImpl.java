package com.platform.analytics.service.impl;

import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.client.SocialMediaClientResolver;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.AnalyticsException;
import com.platform.analytics.exception.ApiException;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.util.AnalyticsCalculator;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AnalyticsSyncServiceImpl implements AnalyticsSyncService {

    private final SocialAccountRepository socialAccountRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SocialMediaClientResolver socialMediaClientResolver;
    private final NotificationService notificationService;

    /**
     * Self-reference obtained through the Spring proxy (not {@code this}),
     * used from {@link #syncAllActiveAccounts()} so each call to
     * {@link #syncAccount(SocialAccount)} goes through the AOP proxy and
     * gets its own transaction. Calling {@code this.syncAccount(...)}
     * directly would bypass the proxy and silently fold every account's
     * sync into one long-lived transaction. {@code @Lazy} breaks the
     * circular-construction dependency this self-injection would otherwise
     * create.
     */
    private final AnalyticsSyncService self;

    public AnalyticsSyncServiceImpl(SocialAccountRepository socialAccountRepository,
                                     AnalyticsRepository analyticsRepository,
                                     SocialMediaClientResolver socialMediaClientResolver,
                                     NotificationService notificationService,
                                     @Lazy AnalyticsSyncService self) {
        this.socialAccountRepository = socialAccountRepository;
        this.analyticsRepository = analyticsRepository;
        this.socialMediaClientResolver = socialMediaClientResolver;
        this.notificationService = notificationService;
        this.self = self;
    }

    @Override
    @Transactional
    public void syncAccount(SocialAccount account) {
        try {
            SocialMediaClient client = socialMediaClientResolver.resolve(account.getPlatform());
            LocalDate today = LocalDate.now();

            SocialMediaClient.DailyMetrics metrics = client.fetchDailyMetrics(account, today);

            Analytics analytics = analyticsRepository
                    .findBySocialAccountIdAndAnalyticsDate(account.getId(), today)
                    .orElseGet(() -> Analytics.builder()
                            .socialAccount(account)
                            .analyticsDate(today)
                            .build());

            analytics.setFollowers(metrics.followers());
            analytics.setFollowing(metrics.following());
            analytics.setImpressions(metrics.impressions());
            analytics.setReach(metrics.reach());
            analytics.setProfileVisits(metrics.profileVisits());
            analytics.setViews(metrics.views());
            analytics.setWatchTime(metrics.watchTime());
            analytics.setLikes(metrics.likes());
            analytics.setComments(metrics.comments());
            analytics.setShares(metrics.shares());
            analytics.setSaves(metrics.saves());
            analytics.setPosts(metrics.posts());
            analytics.setEngagementRate(AnalyticsCalculator.engagementRate(
                    metrics.likes(), metrics.comments(), metrics.shares(), metrics.followers()));

            analyticsRepository.save(analytics);

            account.setLastSynced(LocalDateTime.now());
            socialAccountRepository.save(account);

            log.info("Synchronized analytics for account={} platform={} date={}",
                    account.getId(), account.getPlatform(), today);
        } catch (ApiException ex) {
            // Already a typed, meaningful exception (e.g. ExternalApiException
            // from a real platform client) — propagate as-is rather than
            // masking it behind a generic AnalyticsException.
            log.error("Failed to sync account={} platform={}: {}", account.getId(), account.getPlatform(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to sync account={} platform={}: {}", account.getId(), account.getPlatform(), ex.getMessage());
            throw new AnalyticsException("Failed to synchronize account " + account.getId(), ex);
        }
    }

    @Override
    public void syncAllActiveAccounts() {
        List<SocialAccount> activeAccounts = socialAccountRepository.findAllActiveAccounts();
        log.info("Starting scheduled synchronization for {} active accounts", activeAccounts.size());

        int successCount = 0;
        int failureCount = 0;

        for (SocialAccount account : activeAccounts) {
            try {
                // Call through the proxy (self), not `this`, so each
                // account's sync gets its own transaction instead of all
                // of them silently sharing this method's (now removed)
                // transaction boundary.
                self.syncAccount(account);
                successCount++;
            } catch (Exception ex) {
                failureCount++;
                log.error("Scheduled sync failed for account={}: {}", account.getId(), ex.getMessage());
                notifySyncFailure(account);
            }
        }

        log.info("Scheduled synchronization complete: {} succeeded, {} failed", successCount, failureCount);
    }

    /**
     * Best-effort, same reasoning as SocialAccountServiceImpl's
     * notifyAccountConnected: a failure creating the notification must not
     * disrupt the sync loop for the remaining accounts.
     */
    private void notifySyncFailure(SocialAccount account) {
        try {
            notificationService.create(
                    account.getUserId(),
                    NotificationType.SYNC_FAILURE,
                    "We couldn't sync your " + account.getPlatform().getDisplayName()
                            + " account. We'll try again on the next scheduled sync.");
        } catch (Exception ex) {
            log.warn("Failed to send sync-failure notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }
}

