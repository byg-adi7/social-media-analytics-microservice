package com.platform.notification.dto.response;

import com.platform.notification.constant.ReportStatus;
import com.platform.notification.constant.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lighter-weight shape for listing reports - omits the (potentially large)
 * CSV content, which is only returned when fetching a single report.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryResponse {
    private UUID id;
    private ReportType reportType;
    private LocalDate startPeriod;
    private LocalDate endPeriod;
    private ReportStatus status;
    private LocalDateTime generatedAt;
}
