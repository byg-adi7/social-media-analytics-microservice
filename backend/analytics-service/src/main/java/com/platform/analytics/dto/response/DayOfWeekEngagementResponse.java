package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response for {@code GET /api/analytics/engagement-by-day} - real,
 * per-day-of-week average engagement rate computed from the user's own
 * synced Analytics rows (Monday..Sunday, in that order). No hour-of-day
 * dimension exists anywhere in the data model (Analytics rows are one per
 * account per calendar day), so this intentionally stops at day-of-week
 * granularity rather than fabricating an hourly breakdown.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayOfWeekEngagementResponse {

    private List<DayBucket> days;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayBucket {
        private String dayOfWeek;
        private double averageEngagementRate;
        private boolean hasData;
    }
}
