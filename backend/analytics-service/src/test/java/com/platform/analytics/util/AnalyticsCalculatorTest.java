package com.platform.analytics.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCalculatorTest {

    @Test
    void engagementRate_calculatesCorrectly() {
        double rate = AnalyticsCalculator.engagementRate(100, 20, 10, 1000);
        assertThat(rate).isEqualTo(13.0);
    }

    @Test
    void engagementRate_returnsZero_whenFollowersIsZero() {
        double rate = AnalyticsCalculator.engagementRate(100, 20, 10, 0);
        assertThat(rate).isZero();
    }

    @Test
    void growthRate_calculatesPositiveGrowth() {
        double rate = AnalyticsCalculator.growthRate(1000, 1200);
        assertThat(rate).isEqualTo(20.0);
    }

    @Test
    void growthRate_calculatesNegativeGrowth() {
        double rate = AnalyticsCalculator.growthRate(1000, 800);
        assertThat(rate).isEqualTo(-20.0);
    }

    @Test
    void growthRate_handlesZeroStartValue() {
        assertThat(AnalyticsCalculator.growthRate(0, 500)).isEqualTo(100.0);
        assertThat(AnalyticsCalculator.growthRate(0, 0)).isZero();
    }

    @Test
    void difference_calculatesCorrectly() {
        assertThat(AnalyticsCalculator.difference(500, 750)).isEqualTo(250);
    }

    @Test
    void movingAverage_computesShrinkingThenFullWindow() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 50L);
        List<Double> result = AnalyticsCalculator.movingAverage(values, 3);

        assertThat(result).containsExactly(10.0, 15.0, 20.0, 30.0, 40.0);
    }

    @Test
    void percentageIncrease_calculatesCorrectly() {
        assertThat(AnalyticsCalculator.percentageIncrease(200, 250)).isEqualTo(25.0);
    }

    @Test
    void percentageDecrease_calculatesCorrectly() {
        assertThat(AnalyticsCalculator.percentageDecrease(200, 150)).isEqualTo(25.0);
    }

    @Test
    void trendDirection_classifiesCorrectly() {
        assertThat(AnalyticsCalculator.trendDirection(5.0)).isEqualTo("UP");
        assertThat(AnalyticsCalculator.trendDirection(-5.0)).isEqualTo("DOWN");
        assertThat(AnalyticsCalculator.trendDirection(0.2)).isEqualTo("STABLE");
    }
}
