package com.platform.analytics.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared aggregation helpers used by {@link DashboardServiceImpl},
 * {@link AnalyticsQueryServiceImpl} and {@link ChartServiceImpl} to avoid
 * duplicating account/analytics lookup logic across services.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsAggregationHelper {

    private final SocialAccountRepository socialAccountRepository;
    private final AnalyticsRepository analyticsRepository;

    /**
     * Returns the most recent analytics snapshot for every active account
     * belonging to the user — used for "current state" KPIs like total
     * followers right now.
     */
    public List<Analytics> getLatestAnalyticsPerActiveAccount(UUID userId) {
        List<SocialAccount> accounts = socialAccountRepository.findAllByUserIdAndActiveTrue(userId);
        return accounts.stream()
                .map(account -> analyticsRepository.findLatestBySocialAccountId(account.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Returns all analytics rows for a user within a date range, optionally
     * filtered to a single platform.
     */
    public List<Analytics> getAnalyticsInRange(UUID userId, LocalDate startDate, LocalDate endDate, Platform platform) {
        if (platform != null) {
            return analyticsRepository.findAllByUserIdAndPlatformAndDateRange(userId, platform, startDate, endDate);
        }
        return analyticsRepository.findAllByUserIdAndDateRange(userId, startDate, endDate);
    }

    public List<SocialAccount> getActiveAccounts(UUID userId) {
        return socialAccountRepository.findAllByUserIdAndActiveTrue(userId);
    }
}
