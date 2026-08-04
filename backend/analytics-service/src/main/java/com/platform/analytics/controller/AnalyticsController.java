package com.platform.analytics.controller;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints providing reports, trends, platform comparisons, summaries,
 * top-platform lookup, engagement analysis and growth predictions.
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Reports, trends, comparisons and growth analytics")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/report")
    @Operation(summary = "Get a full analytics report",
            description = "Combines summary, platform comparison, audience growth, engagement analysis, " +
                    "top posts, best/worst days and recommendations.")
    public ReportResponse getReport(@Parameter(description = "Start date (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                     @Parameter(description = "End date (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                     @Parameter(description = "Optional platform filter") @RequestParam(required = false) Platform platform) {
        log.info("Incoming request: get analytics report");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getReport(userId, buildQuery(startDate, endDate, platform));
    }

    @GetMapping("/trends")
    @Operation(summary = "Get time-series trends", description = "Returns follower/view/engagement trends with a 7-day moving average.")
    public TrendResponse getTrends(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                    @RequestParam(required = false) Platform platform) {
        log.info("Incoming request: get analytics trends");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getTrends(userId, buildQuery(startDate, endDate, platform));
    }

    @GetMapping("/platform-comparison")
    @Operation(summary = "Compare metrics across all connected platforms")
    public List<PlatformMetricsResponse> getPlatformComparison(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get platform comparison");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getPlatformComparison(userId, buildQuery(startDate, endDate, null));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get a condensed analytics summary")
    public AnalyticsSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get analytics summary");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getSummary(userId, buildQuery(startDate, endDate, null));
    }

    @GetMapping("/top-platform")
    @Operation(summary = "Get the best performing platform by engagement rate")
    public PlatformMetricsResponse getTopPlatform(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get top platform");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getTopPlatform(userId, buildQuery(startDate, endDate, null));
    }

    @GetMapping("/engagement")
    @Operation(summary = "Get engagement analysis for a date range")
    public EngagementResponse getEngagement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Platform platform) {
        log.info("Incoming request: get engagement analysis");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getEngagement(userId, buildQuery(startDate, endDate, platform));
    }

    @GetMapping("/growth")
    @Operation(summary = "Get audience growth analysis and 30-day prediction")
    public GrowthResponse getGrowth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Platform platform) {
        log.info("Incoming request: get growth analysis");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getGrowth(userId, buildQuery(startDate, endDate, platform));
    }

    @GetMapping("/engagement-by-day")
    @Operation(summary = "Get real average engagement rate by day of week",
            description = "Groups the user's own synced Analytics rows by day-of-week (Monday..Sunday) and " +
                    "averages engagement rate per day - no hour-of-day breakdown exists since Analytics rows " +
                    "are one per account per calendar day.")
    public DayOfWeekEngagementResponse getEngagementByDayOfWeek(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Platform platform) {
        log.info("Incoming request: get engagement by day of week");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return analyticsQueryService.getEngagementByDayOfWeek(userId, buildQuery(startDate, endDate, platform));
    }

    private AnalyticsQueryRequest buildQuery(LocalDate startDate, LocalDate endDate, Platform platform) {
        return AnalyticsQueryRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .platform(platform)
                .build();
    }
}
