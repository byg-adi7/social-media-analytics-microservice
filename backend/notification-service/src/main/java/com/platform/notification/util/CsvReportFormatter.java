package com.platform.notification.util;

import com.platform.notification.dto.response.AnalyticsSummary;
import com.platform.notification.dto.response.PlatformMetrics;

import java.util.List;

/**
 * Hand-rolled CSV formatting - no library needed since every field here is
 * a simple number/enum-name/plain string with no embedded commas or quotes
 * to escape.
 */
public final class CsvReportFormatter {

    private CsvReportFormatter() {
    }

    public static String forPlatformComparison(List<PlatformMetrics> metrics) {
        StringBuilder csv = new StringBuilder(
                "platform,followers,views,likes,comments,shares,posts,engagementRate,growthRate\n");
        for (PlatformMetrics m : metrics) {
            csv.append(m.getPlatform()).append(',')
                    .append(m.getFollowers()).append(',')
                    .append(m.getViews()).append(',')
                    .append(m.getLikes()).append(',')
                    .append(m.getComments()).append(',')
                    .append(m.getShares()).append(',')
                    .append(m.getPosts()).append(',')
                    .append(m.getEngagementRate()).append(',')
                    .append(m.getGrowthRate()).append('\n');
        }
        return csv.toString();
    }

    public static String forSummary(AnalyticsSummary summary) {
        return "metric,value\n"
                + "totalFollowers," + summary.getTotalFollowers() + '\n'
                + "totalPosts," + summary.getTotalPosts() + '\n'
                + "averageEngagementRate," + summary.getAverageEngagementRate() + '\n'
                + "averageDailyViews," + summary.getAverageDailyViews() + '\n'
                + "averageReach," + summary.getAverageReach() + '\n'
                + "bestPlatform," + nullToEmpty(summary.getBestPlatform()) + '\n'
                + "worstPlatform," + nullToEmpty(summary.getWorstPlatform()) + '\n'
                + "fastestGrowingPlatform," + nullToEmpty(summary.getFastestGrowingPlatform()) + '\n'
                + "mostActivePlatform," + nullToEmpty(summary.getMostActivePlatform()) + '\n'
                + "mostViewedPlatform," + nullToEmpty(summary.getMostViewedPlatform()) + '\n';
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
