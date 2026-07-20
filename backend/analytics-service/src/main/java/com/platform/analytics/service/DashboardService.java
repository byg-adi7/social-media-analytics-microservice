package com.platform.analytics.service;

import com.platform.analytics.dto.response.DashboardResponse;

import java.util.UUID;

/**
 * Provides the aggregated KPI data shown on the main dashboard.
 */
public interface DashboardService {

    DashboardResponse getDashboard(UUID userId);
}
