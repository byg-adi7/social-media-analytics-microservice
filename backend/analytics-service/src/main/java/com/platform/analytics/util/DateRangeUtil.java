package com.platform.analytics.util;

import java.time.LocalDate;

/**
 * Helper for resolving default date ranges when the client does not supply
 * explicit {@code startDate}/{@code endDate} query parameters.
 */
public final class DateRangeUtil {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private DateRangeUtil() {
    }

    public static LocalDate resolveStartDate(LocalDate startDate) {
        return startDate != null ? startDate : LocalDate.now().minusDays(DEFAULT_RANGE_DAYS);
    }

    public static LocalDate resolveEndDate(LocalDate endDate) {
        return endDate != null ? endDate : LocalDate.now();
    }
}
