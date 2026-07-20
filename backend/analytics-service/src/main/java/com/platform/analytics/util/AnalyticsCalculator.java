package com.platform.analytics.util;

import com.platform.analytics.entity.Analytics;

import java.util.List;

/**
 * Pure, stateless utility housing every core analytics calculation used
 * across services: engagement rate, growth rate, averages, moving average,
 * and percentage change. Kept separate from services for testability and
 * single-responsibility.
 */
public final class AnalyticsCalculator {

    private AnalyticsCalculator() {
    }

    /**
     * Engagement Rate = (Likes + Comments + Shares) / Followers × 100
     */
    public static double engagementRate(long likes, long comments, long shares, long followers) {
        if (followers <= 0) {
            return 0.0;
        }
        return round2((likes + comments + shares) * 100.0 / followers);
    }

    /**
     * Growth Rate (%) between a starting and ending value.
     */
    public static double growthRate(long startValue, long endValue) {
        if (startValue <= 0) {
            return endValue > 0 ? 100.0 : 0.0;
        }
        return round2((endValue - startValue) * 100.0 / startValue);
    }

    public static long difference(long startValue, long endValue) {
        return endValue - startValue;
    }

    public static double averageDailyViews(List<Analytics> analyticsList) {
        return average(analyticsList.stream().mapToLong(Analytics::getViews));
    }

    public static double averageReach(List<Analytics> analyticsList) {
        return average(analyticsList.stream().mapToLong(Analytics::getReach));
    }

    public static double averageFollowers(List<Analytics> analyticsList) {
        return average(analyticsList.stream().mapToLong(Analytics::getFollowers));
    }

    private static double average(java.util.stream.LongStream stream) {
        long[] values = stream.toArray();
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (long v : values) {
            sum += v;
        }
        return round2(sum / values.length);
    }

    /**
     * Simple moving average over a window size, aligned index-for-index with
     * the input list (early points use a shrinking window).
     */
    public static List<Double> movingAverage(List<Long> values, int windowSize) {
        List<Double> result = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - windowSize + 1);
            List<Long> window = values.subList(start, i + 1);
            double avg = window.stream().mapToLong(Long::longValue).average().orElse(0.0);
            result.add(round2(avg));
        }
        return result;
    }

    public static double percentageIncrease(double oldValue, double newValue) {
        if (oldValue <= 0) {
            return newValue > 0 ? 100.0 : 0.0;
        }
        return round2(((newValue - oldValue) / oldValue) * 100.0);
    }

    public static double percentageDecrease(double oldValue, double newValue) {
        double increase = percentageIncrease(oldValue, newValue);
        return increase < 0 ? Math.abs(increase) : 0.0;
    }

    public static String trendDirection(double percentageChange) {
        if (percentageChange > 1.0) {
            return "UP";
        } else if (percentageChange < -1.0) {
            return "DOWN";
        }
        return "STABLE";
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
