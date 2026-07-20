package com.platform.notification.client;

import com.platform.notification.dto.response.AnalyticsSummary;
import com.platform.notification.dto.response.PlatformMetrics;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Pulls real analytics data to build a report. Forwards the caller's own
 * bearer token rather than using a separate service-to-service credential -
 * the Analytics Service already validates that token against the Auth
 * Service and scopes the data to whichever user it belongs to, so this
 * naturally produces a report for the right user with no separate identity
 * plumbing needed.
 */
@FeignClient(name = "analytics-service", url = "${analytics-service.url}")
public interface AnalyticsServiceClient {

    @GetMapping("/api/analytics/platform-comparison")
    List<PlatformMetrics> getPlatformComparison(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate);

    @GetMapping("/api/analytics/summary")
    AnalyticsSummary getSummary(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate);
}
