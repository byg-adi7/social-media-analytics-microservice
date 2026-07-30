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

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private UUID id;
    private ReportType reportType;
    private LocalDate startPeriod;
    private LocalDate endPeriod;
    private ReportStatus status;
    private String content;
    private String errorMessage;
    private LocalDateTime generatedAt;
}
