package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Represents a single top-performing post/video, used by the report and by
 * {@code GET /api/charts/top-content}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopContentResponse {

    private Platform platform;
    private String title;
    private LocalDate publishedDate;
    private long views;
    private long likes;
    private long comments;
    private long shares;
    private double engagementRate;
}
