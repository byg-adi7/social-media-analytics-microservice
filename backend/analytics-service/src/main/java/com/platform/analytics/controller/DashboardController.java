package com.platform.analytics.controller;

import com.platform.analytics.dto.response.DashboardResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Serves the aggregated KPI data for the main creator dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated cross-platform KPI dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get the current user's aggregated dashboard",
            description = "Returns total followers, views, likes, comments, shares, reach, impressions, " +
                    "average engagement, connected platforms, best performing platform and last sync time.")
    public DashboardResponse getDashboard() {
        log.info("Incoming request: get dashboard");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return dashboardService.getDashboard(userId);
    }
}
