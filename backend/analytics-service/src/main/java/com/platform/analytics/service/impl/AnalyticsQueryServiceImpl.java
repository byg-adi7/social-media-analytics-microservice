package com.platform.analytics.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.exception.AnalyticsException;
import com.platform.analytics.service.AnalyticsQueryService;
import com.platform.analytics.service.ChartService;
import com.platform.analytics.util.AnalyticsCalculator;
import com.platform.analytics.util.DateRangeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every method here is read-only, but still needs an active transaction:
 * with spring.jpa.open-in-view disabled (deliberately - keeping the
 * Hibernate session open through view rendering is an anti-pattern),
 * Analytics.socialAccount (a lazy @ManyToOne) can only be resolved while
 * the session from the repository call is still open. Without this, every
 * method below that reads socialAccount.getPlatform() after the
 * aggregation helper's query returns throws LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM d");
    private static final int REPORT_TOP_POSTS_LIMIT = 10;

    private final AnalyticsAggregationHelper aggregationHelper;
    private final ChartService chartService;

    @Override
    public ReportResponse getReport(UUID userId, AnalyticsQueryRequest query) {
        log.info("Generating analytics report for user {}", userId);
        try {
            AnalyticsSummaryResponse summary = getSummary(userId, query);
            List<PlatformMetricsResponse> platformComparison = getPlatformComparison(userId, query);
            GrowthResponse growth = getGrowth(userId, query);
            EngagementResponse engagement = getEngagement(userId, query);
            List<TopContentResponse> topPosts = chartService.getTopContentChart(userId, REPORT_TOP_POSTS_LIMIT);
            List<String> bestDays = findTopDays(userId, query, true);
            List<String> worstDays = findTopDays(userId, query, false);
            List<String> recommendations = buildRecommendations(summary, growth, engagement);

            return ReportResponse.builder()
                    .summary(summary)
                    .platformComparison(platformComparison)
                    .audienceGrowth(growth)
                    .engagementAnalysis(engagement)
                    .topPosts(topPosts)
                    .bestDays(bestDays)
                    .worstDays(worstDays)
                    .recommendations(recommendations)
                    .build();
        } catch (Exception ex) {
            throw new AnalyticsException("Failed to generate analytics report", ex);
        }
    }

    @Override
    public TrendResponse getTrends(UUID userId, AnalyticsQueryRequest query) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());

        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, query.getPlatform());
        Map<LocalDate, List<Analytics>> byDate = analyticsList.stream()
                .collect(Collectors.groupingBy(Analytics::getAnalyticsDate));

        List<LocalDate> sortedDates = new ArrayList<>(byDate.keySet());
        sortedDates.sort(Comparator.naturalOrder());

        List<String> labels = sortedDates.stream().map(d -> d.format(LABEL_FORMAT)).toList();
        List<Long> followers = sortedDates.stream()
                .map(d -> byDate.get(d).stream().mapToLong(Analytics::getFollowers).sum())
                .toList();
        List<Long> views = sortedDates.stream()
                .map(d -> byDate.get(d).stream().mapToLong(Analytics::getViews).sum())
                .toList();
        List<Double> engagementRate = sortedDates.stream()
                .map(d -> AnalyticsCalculator.round2(
                        byDate.get(d).stream().mapToDouble(Analytics::getEngagementRate).average().orElse(0.0)))
                .toList();

        List<Double> movingAvg = AnalyticsCalculator.movingAverage(followers, 7);

        double percentageChange = followers.size() >= 2
                ? AnalyticsCalculator.percentageIncrease(followers.get(0), followers.get(followers.size() - 1))
                : 0.0;

        return TrendResponse.builder()
                .labels(labels)
                .followers(followers)
                .views(views)
                .engagementRate(engagementRate)
                .movingAverage(movingAvg)
                .percentageChange(percentageChange)
                .trendDirection(AnalyticsCalculator.trendDirection(percentageChange))
                .build();
    }

    @Override
    public List<PlatformMetricsResponse> getPlatformComparison(UUID userId, AnalyticsQueryRequest query) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());

        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, null);
        Map<Platform, List<Analytics>> byPlatform = analyticsList.stream()
                .collect(Collectors.groupingBy(a -> a.getSocialAccount().getPlatform()));

        List<PlatformMetricsResponse> result = new ArrayList<>();
        for (Map.Entry<Platform, List<Analytics>> entry : byPlatform.entrySet()) {
            List<Analytics> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(Analytics::getAnalyticsDate))
                    .toList();

            long latestFollowers = sorted.get(sorted.size() - 1).getFollowers();
            long firstFollowers = sorted.get(0).getFollowers();

            result.add(PlatformMetricsResponse.builder()
                    .platform(entry.getKey())
                    .followers(latestFollowers)
                    .views(sorted.stream().mapToLong(Analytics::getViews).sum())
                    .likes(sorted.stream().mapToLong(Analytics::getLikes).sum())
                    .comments(sorted.stream().mapToLong(Analytics::getComments).sum())
                    .shares(sorted.stream().mapToLong(Analytics::getShares).sum())
                    .posts(sorted.stream().mapToLong(Analytics::getPosts).sum())
                    .engagementRate(AnalyticsCalculator.round2(
                            sorted.stream().mapToDouble(Analytics::getEngagementRate).average().orElse(0.0)))
                    .growthRate(AnalyticsCalculator.growthRate(firstFollowers, latestFollowers))
                    .build());
        }

        result.sort(Comparator.comparingLong(PlatformMetricsResponse::getFollowers).reversed());
        return result;
    }

    @Override
    public AnalyticsSummaryResponse getSummary(UUID userId, AnalyticsQueryRequest query) {
        List<PlatformMetricsResponse> comparison = getPlatformComparison(userId, query);
        List<Analytics> latest = aggregationHelper.getLatestAnalyticsPerActiveAccount(userId);

        long totalFollowers = latest.stream().mapToLong(Analytics::getFollowers).sum();
        long totalPosts = latest.stream().mapToLong(Analytics::getPosts).sum();
        double avgEngagement = AnalyticsCalculator.round2(
                latest.stream().mapToDouble(Analytics::getEngagementRate).average().orElse(0.0));
        double avgDailyViews = AnalyticsCalculator.averageDailyViews(latest);
        double avgReach = AnalyticsCalculator.averageReach(latest);

        String bestPlatform = comparison.stream()
                .max(Comparator.comparingDouble(PlatformMetricsResponse::getEngagementRate))
                .map(p -> p.getPlatform().getDisplayName()).orElse(null);
        String worstPlatform = comparison.stream()
                .min(Comparator.comparingDouble(PlatformMetricsResponse::getEngagementRate))
                .map(p -> p.getPlatform().getDisplayName()).orElse(null);
        String fastestGrowing = comparison.stream()
                .max(Comparator.comparingDouble(PlatformMetricsResponse::getGrowthRate))
                .map(p -> p.getPlatform().getDisplayName()).orElse(null);
        String mostActive = comparison.stream()
                .max(Comparator.comparingLong(PlatformMetricsResponse::getPosts))
                .map(p -> p.getPlatform().getDisplayName()).orElse(null);
        String mostViewed = comparison.stream()
                .max(Comparator.comparingLong(PlatformMetricsResponse::getViews))
                .map(p -> p.getPlatform().getDisplayName()).orElse(null);

        return AnalyticsSummaryResponse.builder()
                .totalFollowers(totalFollowers)
                .totalPosts(totalPosts)
                .averageEngagementRate(avgEngagement)
                .averageDailyViews(avgDailyViews)
                .averageReach(avgReach)
                .bestPlatform(bestPlatform)
                .worstPlatform(worstPlatform)
                .fastestGrowingPlatform(fastestGrowing)
                .mostActivePlatform(mostActive)
                .mostViewedPlatform(mostViewed)
                .build();
    }

    @Override
    public PlatformMetricsResponse getTopPlatform(UUID userId, AnalyticsQueryRequest query) {
        return getPlatformComparison(userId, query).stream()
                .max(Comparator.comparingDouble(PlatformMetricsResponse::getEngagementRate))
                .orElseThrow(() -> new AnalyticsException("No platform data available to determine top platform"));
    }

    @Override
    public EngagementResponse getEngagement(UUID userId, AnalyticsQueryRequest query) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());
        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, query.getPlatform());

        double overallRate = AnalyticsCalculator.round2(
                analyticsList.stream().mapToDouble(Analytics::getEngagementRate).average().orElse(0.0));

        long totalLikes = analyticsList.stream().mapToLong(Analytics::getLikes).sum();
        long totalComments = analyticsList.stream().mapToLong(Analytics::getComments).sum();
        long totalShares = analyticsList.stream().mapToLong(Analytics::getShares).sum();
        long totalSaves = analyticsList.stream().mapToLong(Analytics::getSaves).sum();

        String bestDay = analyticsList.stream()
                .max(Comparator.comparingDouble(Analytics::getEngagementRate))
                .map(a -> a.getAnalyticsDate().toString()).orElse(null);
        String worstDay = analyticsList.stream()
                .min(Comparator.comparingDouble(Analytics::getEngagementRate))
                .map(a -> a.getAnalyticsDate().toString()).orElse(null);

        return EngagementResponse.builder()
                .overallEngagementRate(overallRate)
                .totalLikes(totalLikes)
                .totalComments(totalComments)
                .totalShares(totalShares)
                .totalSaves(totalSaves)
                .bestEngagementDay(bestDay)
                .worstEngagementDay(worstDay)
                .build();
    }

    @Override
    public GrowthResponse getGrowth(UUID userId, AnalyticsQueryRequest query) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());
        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, query.getPlatform())
                .stream()
                .sorted(Comparator.comparing(Analytics::getAnalyticsDate))
                .toList();

        if (analyticsList.isEmpty()) {
            return GrowthResponse.builder()
                    .startFollowers(0).endFollowers(0).followerDifference(0)
                    .growthRatePercentage(0).averageDailyGrowth(0)
                    .averageWeeklyGrowth(0).averageMonthlyGrowth(0)
                    .predictedFollowersNext30Days(0)
                    .build();
        }

        // Aggregate total followers per day across all platforms/accounts in scope.
        Map<LocalDate, Long> followersByDate = analyticsList.stream()
                .collect(Collectors.groupingBy(Analytics::getAnalyticsDate,
                        Collectors.summingLong(Analytics::getFollowers)));

        List<LocalDate> dates = new ArrayList<>(followersByDate.keySet());
        dates.sort(Comparator.naturalOrder());

        long startFollowers = followersByDate.get(dates.get(0));
        long endFollowers = followersByDate.get(dates.get(dates.size() - 1));
        long difference = AnalyticsCalculator.difference(startFollowers, endFollowers);
        double growthRate = AnalyticsCalculator.growthRate(startFollowers, endFollowers);

        long daySpan = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1)));
        double avgDaily = AnalyticsCalculator.round2((double) difference / daySpan);
        double avgWeekly = AnalyticsCalculator.round2(avgDaily * 7);
        double avgMonthly = AnalyticsCalculator.round2(avgDaily * 30);

        long predicted = endFollowers + Math.round(avgDaily * 30);

        return GrowthResponse.builder()
                .startFollowers(startFollowers)
                .endFollowers(endFollowers)
                .followerDifference(difference)
                .growthRatePercentage(growthRate)
                .averageDailyGrowth(avgDaily)
                .averageWeeklyGrowth(avgWeekly)
                .averageMonthlyGrowth(avgMonthly)
                .predictedFollowersNext30Days(predicted)
                .build();
    }

    private List<String> findTopDays(UUID userId, AnalyticsQueryRequest query, boolean best) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());
        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, query.getPlatform());

        Map<LocalDate, Double> engagementByDate = analyticsList.stream()
                .collect(Collectors.groupingBy(Analytics::getAnalyticsDate,
                        Collectors.averagingDouble(Analytics::getEngagementRate)));

        Comparator<Map.Entry<LocalDate, Double>> comparator = Map.Entry.comparingByValue();
        if (best) {
            comparator = comparator.reversed();
        }

        return engagementByDate.entrySet().stream()
                .sorted(comparator)
                .limit(3)
                .map(e -> e.getKey().toString())
                .toList();
    }

    private List<String> buildRecommendations(AnalyticsSummaryResponse summary, GrowthResponse growth,
                                               EngagementResponse engagement) {
        List<String> recommendations = new ArrayList<>();

        if (growth.getGrowthRatePercentage() < 1.0) {
            recommendations.add("Growth has slowed — consider increasing posting frequency on "
                    + (summary.getMostActivePlatform() != null ? summary.getMostActivePlatform() : "your top platform") + ".");
        } else {
            recommendations.add("Great momentum! Followers grew " + growth.getGrowthRatePercentage()
                    + "% in this period — keep up the current content cadence.");
        }

        if (summary.getWorstPlatform() != null) {
            recommendations.add("Engagement on " + summary.getWorstPlatform()
                    + " is lagging behind your other platforms — try experimenting with new content formats there.");
        }

        if (engagement.getOverallEngagementRate() < 2.0) {
            recommendations.add("Overall engagement rate is below 2% — prioritize posts that prompt comments and shares.");
        }

        if (summary.getBestPlatform() != null) {
            recommendations.add(summary.getBestPlatform()
                    + " is your best performing platform — consider repurposing its top content across other platforms.");
        }

        return recommendations;
    }
}
