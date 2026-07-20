package com.platform.analytics.dto.request;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Common query parameters accepted by analytics/report/chart endpoints for
 * filtering by date range and/or platform. Bound from request parameters,
 * not the request body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsQueryRequest {

    private LocalDate startDate;

    private LocalDate endDate;

    private Platform platform;
}
