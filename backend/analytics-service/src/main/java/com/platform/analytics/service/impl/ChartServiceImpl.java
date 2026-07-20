package com.platform.analytics.service.impl;

import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.client.SocialMediaClientResolver;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.service.ChartService;
import com.platform.analytics.util.DateRangeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartServiceImpl implements ChartService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final AnalyticsAggregationHelper aggregationHelper;
    private final SocialMediaClientResolver socialMediaClientResolver;

    @Override
    public MultiLineChartResponse getEngagementChart(UUID userId, AnalyticsQueryRequest query) {
        return buildMultiLineChart(userId, query, a -> Math.round(a.getEngagementRate()));
    }

    @Override
    public MultiLineChartResponse getFollowersChart(UUID userId, AnalyticsQueryRequest query) {
        return buildMultiLineChart(userId, query, Analytics::getFollowers);
    }

    @Override
    public MultiLineChartResponse getViewsChart(UUID userId, AnalyticsQueryRequest query) {
        return buildMultiLineChart(userId, query, Analytics::getViews);
    }

    @Override
    public List<BarChartItemResponse> getPlatformComparisonChart(UUID userId) {
        List<Analytics> latest = aggregationHelper.getLatestAnalyticsPerActiveAccount(userId);

        Map<Platform, Long> followersByPlatform = latest.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getSocialAccount().getPlatform(),
                        Collectors.summingLong(Analytics::getFollowers)));

        return followersByPlatform.entrySet().stream()
                .map(e -> BarChartItemResponse.builder().platform(e.getKey()).followers(e.getValue()).build())
                .sorted(Comparator.comparingLong(BarChartItemResponse::getFollowers).reversed())
                .toList();
    }

    @Override
    public List<PieChartItemResponse> getEngagementDistributionChart(UUID userId) {
        List<Analytics> latest = aggregationHelper.getLatestAnalyticsPerActiveAccount(userId);

        Map<Platform, Double> engagementByPlatform = latest.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getSocialAccount().getPlatform(),
                        Collectors.summingDouble(a -> a.getLikes() + a.getComments() + a.getShares())));

        double total = engagementByPlatform.values().stream().mapToDouble(Double::doubleValue).sum();

        return engagementByPlatform.entrySet().stream()
                .map(e -> PieChartItemResponse.builder()
                        .platform(e.getKey())
                        .value(total > 0 ? Math.round((e.getValue() / total) * 1000.0) / 10.0 : 0.0)
                        .build())
                .sorted(Comparator.comparingDouble(PieChartItemResponse::getValue).reversed())
                .toList();
    }

    @Override
    public List<TopContentResponse> getTopContentChart(UUID userId, int limit) {
        List<SocialAccount> accounts = aggregationHelper.getActiveAccounts(userId);

        List<TopContentResponse> allContent = new ArrayList<>();
        for (SocialAccount account : accounts) {
            try {
                SocialMediaClient client = socialMediaClientResolver.resolve(account.getPlatform());
                allContent.addAll(client.fetchTopContent(account, Math.min(limit, 5)));
            } catch (com.platform.analytics.exception.ApiException ex) {
                log.warn("Skipping top content for account={} platform={}: {}",
                        account.getId(), account.getPlatform(), ex.getMessage());
            }
        }

        return allContent.stream()
                .sorted(Comparator.comparingLong(TopContentResponse::getViews).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<AudienceDemographicsResponse> getAudienceDemographicsChart(UUID userId) {
        List<SocialAccount> accounts = aggregationHelper.getActiveAccounts(userId);

        List<AudienceDemographicsResponse> result = new ArrayList<>();
        for (SocialAccount account : accounts) {
            try {
                SocialMediaClient client = socialMediaClientResolver.resolve(account.getPlatform());
                client.fetchAudienceDemographics(account).ifPresent(result::add);
            } catch (com.platform.analytics.exception.ApiException ex) {
                log.warn("Skipping audience demographics for account={} platform={}: {}",
                        account.getId(), account.getPlatform(), ex.getMessage());
            }
        }

        return result;
    }

    @Override
    public GrowthChartResponse getWeeklyGrowthChart(UUID userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, null);
        Map<LocalDate, Long> followersByDate = analyticsList.stream()
                .collect(Collectors.groupingBy(Analytics::getAnalyticsDate,
                        Collectors.summingLong(Analytics::getFollowers)));

        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }

        List<String> labels = days.stream().map(d -> d.format(DAY_LABEL)).toList();
        List<Long> growth = new ArrayList<>();
        Long previous = null;
        for (LocalDate day : days) {
            long current = followersByDate.getOrDefault(day, previous != null ? previous : 0L);
            growth.add(previous == null ? 0L : current - previous);
            previous = current;
        }

        return GrowthChartResponse.builder().labels(labels).followerGrowth(growth).build();
    }

    @Override
    public GrowthChartResponse getMonthlyGrowthChart(UUID userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(5).withDayOfMonth(1);

        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, null);
        Map<YearMonth, Long> followersByMonth = new TreeMap<>();
        for (Analytics a : analyticsList) {
            YearMonth ym = YearMonth.from(a.getAnalyticsDate());
            followersByMonth.merge(ym, a.getFollowers(), Math::max);
        }

        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(end);
        while (!cursor.isAfter(endMonth)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }

        List<String> labels = months.stream().map(m -> m.atDay(1).format(MONTH_LABEL)).toList();
        List<Long> growth = new ArrayList<>();
        Long previous = null;
        for (YearMonth month : months) {
            long current = followersByMonth.getOrDefault(month, previous != null ? previous : 0L);
            growth.add(previous == null ? 0L : current - previous);
            previous = current;
        }

        return GrowthChartResponse.builder().labels(labels).followerGrowth(growth).build();
    }

    private MultiLineChartResponse buildMultiLineChart(UUID userId, AnalyticsQueryRequest query,
                                                         java.util.function.ToLongFunction<Analytics> valueExtractor) {
        LocalDate start = DateRangeUtil.resolveStartDate(query.getStartDate());
        LocalDate end = DateRangeUtil.resolveEndDate(query.getEndDate());

        List<Analytics> analyticsList = aggregationHelper.getAnalyticsInRange(userId, start, end, null);

        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        List<String> labels = days.stream().map(d -> d.format(DAY_LABEL)).toList();

        Map<Platform, Map<LocalDate, Long>> byPlatformAndDate = new EnumMap<>(Platform.class);
        for (Analytics a : analyticsList) {
            Platform platform = a.getSocialAccount().getPlatform();
            byPlatformAndDate
                    .computeIfAbsent(platform, p -> new HashMap<>())
                    .merge(a.getAnalyticsDate(), valueExtractor.applyAsLong(a), Long::sum);
        }

        Map<String, List<Long>> series = new LinkedHashMap<>();
        for (Platform platform : Platform.values()) {
            Map<LocalDate, Long> dateMap = byPlatformAndDate.getOrDefault(platform, Map.of());
            if (dateMap.isEmpty()) {
                continue;
            }
            List<Long> values = days.stream().map(d -> dateMap.getOrDefault(d, 0L)).toList();
            series.put(platform.name().toLowerCase(), values);
        }

        return MultiLineChartResponse.builder().labels(labels).series(series).build();
    }
}
