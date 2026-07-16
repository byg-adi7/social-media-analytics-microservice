package com.platform.analytics.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.DashboardResponse;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.service.DashboardService;
import com.platform.analytics.util.AnalyticsCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AnalyticsAggregationHelper aggregationHelper;

    @Override
    public DashboardResponse getDashboard(UUID userId) {
        List<Analytics> latestPerAccount = aggregationHelper.getLatestAnalyticsPerActiveAccount(userId);
        List<SocialAccount> activeAccounts = aggregationHelper.getActiveAccounts(userId);

        long totalFollowers = sum(latestPerAccount, Analytics::getFollowers);
        long totalViews = sum(latestPerAccount, Analytics::getViews);
        long totalLikes = sum(latestPerAccount, Analytics::getLikes);
        long totalComments = sum(latestPerAccount, Analytics::getComments);
        long totalShares = sum(latestPerAccount, Analytics::getShares);
        long totalReach = sum(latestPerAccount, Analytics::getReach);
        long totalImpressions = sum(latestPerAccount, Analytics::getImpressions);

        double averageEngagement = latestPerAccount.isEmpty() ? 0.0 : AnalyticsCalculator.round2(
                latestPerAccount.stream().mapToDouble(Analytics::getEngagementRate).average().orElse(0.0));

        List<Platform> connectedPlatforms = activeAccounts.stream()
                .map(SocialAccount::getPlatform)
                .distinct()
                .toList();

        Platform bestPlatform = findBestPerformingPlatform(latestPerAccount);

        LocalDateTime lastSyncTime = activeAccounts.stream()
                .map(SocialAccount::getLastSynced)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return DashboardResponse.builder()
                .totalFollowers(totalFollowers)
                .totalViews(totalViews)
                .totalLikes(totalLikes)
                .totalComments(totalComments)
                .totalShares(totalShares)
                .totalReach(totalReach)
                .totalImpressions(totalImpressions)
                .averageEngagement(averageEngagement)
                .connectedPlatforms(connectedPlatforms)
                .bestPerformingPlatform(bestPlatform)
                .lastSyncTime(lastSyncTime)
                .build();
    }

    private Platform findBestPerformingPlatform(List<Analytics> latestPerAccount) {
        Map<Platform, Double> engagementByPlatform = latestPerAccount.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getSocialAccount().getPlatform(),
                        Collectors.averagingDouble(Analytics::getEngagementRate)));

        return engagementByPlatform.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private long sum(List<Analytics> list, java.util.function.ToLongFunction<Analytics> extractor) {
        return list.stream().mapToLong(extractor).sum();
    }
}
