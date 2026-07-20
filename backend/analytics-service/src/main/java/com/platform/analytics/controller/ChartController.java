package com.platform.analytics.controller;

import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.ChartService;
import io.swagger.v3.oas.annotations.Operation;
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
 * Endpoints returning chart-ready JSON datasets for the frontend. This
 * service never renders graphs — the frontend is responsible for that.
 */
@Slf4j
@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
@Tag(name = "Charts", description = "Chart-ready datasets for the frontend to render")
public class ChartController {

    private final ChartService chartService;

    @GetMapping("/engagement")
    @Operation(summary = "Multi-line engagement chart data, one line per platform")
    public MultiLineChartResponse getEngagementChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get engagement chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getEngagementChart(userId, buildQuery(startDate, endDate));
    }

    @GetMapping("/followers")
    @Operation(summary = "Multi-line follower growth chart data, one line per platform")
    public MultiLineChartResponse getFollowersChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get followers chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getFollowersChart(userId, buildQuery(startDate, endDate));
    }

    @GetMapping("/views")
    @Operation(summary = "Multi-line views chart data, one line per platform")
    public MultiLineChartResponse getViewsChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Incoming request: get views chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getViewsChart(userId, buildQuery(startDate, endDate));
    }

    @GetMapping("/platform-comparison")
    @Operation(summary = "Bar chart data comparing followers across platforms")
    public List<BarChartItemResponse> getPlatformComparisonChart() {
        log.info("Incoming request: get platform comparison chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getPlatformComparisonChart(userId);
    }

    @GetMapping("/engagement-distribution")
    @Operation(summary = "Pie chart data showing engagement share per platform")
    public List<PieChartItemResponse> getEngagementDistributionChart() {
        log.info("Incoming request: get engagement distribution chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getEngagementDistributionChart(userId);
    }

    @GetMapping("/top-content")
    @Operation(summary = "Highest performing posts/videos across all connected platforms")
    public List<TopContentResponse> getTopContentChart(@RequestParam(defaultValue = "10") int limit) {
        log.info("Incoming request: get top content chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getTopContentChart(userId, limit);
    }

    @GetMapping("/audience-demographics")
    @Operation(summary = "Follower age/gender/city/country breakdowns, for platforms that support it")
    public List<AudienceDemographicsResponse> getAudienceDemographicsChart() {
        log.info("Incoming request: get audience demographics chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getAudienceDemographicsChart(userId);
    }

    @GetMapping("/weekly-growth")
    @Operation(summary = "Daily follower growth over the last 7 days")
    public GrowthChartResponse getWeeklyGrowthChart() {
        log.info("Incoming request: get weekly growth chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getWeeklyGrowthChart(userId);
    }

    @GetMapping("/monthly-growth")
    @Operation(summary = "Monthly audience growth over the last 6 months")
    public GrowthChartResponse getMonthlyGrowthChart() {
        log.info("Incoming request: get monthly growth chart");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return chartService.getMonthlyGrowthChart(userId);
    }

    private AnalyticsQueryRequest buildQuery(LocalDate startDate, LocalDate endDate) {
        return AnalyticsQueryRequest.builder().startDate(startDate).endDate(endDate).build();
    }
}
