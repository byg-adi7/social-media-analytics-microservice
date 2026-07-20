package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a single day's analytics snapshot.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsResponse {

    private UUID id;
    private UUID socialAccountId;
    private LocalDate analyticsDate;
    private long followers;
    private long following;
    private long impressions;
    private long reach;
    private long profileVisits;
    private long views;
    private double watchTime;
    private long likes;
    private long comments;
    private long shares;
    private long saves;
    private long posts;
    private double engagementRate;
}
