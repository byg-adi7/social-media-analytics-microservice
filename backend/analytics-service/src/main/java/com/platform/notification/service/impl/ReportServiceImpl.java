package com.platform.notification.service.impl;

import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.AnalyticsSummaryResponse;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.dto.response.PlatformMetricsResponse;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.service.AnalyticsQueryService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.constant.ReportStatus;
import com.platform.notification.dto.request.CreateReportRequest;
import com.platform.notification.dto.response.ReportResponse;
import com.platform.notification.dto.response.ReportSummaryResponse;
import com.platform.notification.entity.Report;
import com.platform.notification.repository.ReportRepository;
import com.platform.notification.service.NotificationService;
import com.platform.notification.service.ReportService;
import com.platform.notification.util.CsvReportFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final AnalyticsQueryService analyticsQueryService;
    private final NotificationService notificationService;

    /**
     * Deliberately not @Transactional: on a failed analytics lookup, the
     * FAILED report row below must still persist even though this method
     * itself then throws - wrapping the whole method in one transaction
     * would roll that save back along with the exception. Each
     * repository.save() already commits in its own transaction via Spring
     * Data JPA's repository proxy.
     */
    @Override
    public ReportResponse generate(UUID userId, CreateReportRequest request) {
        if (request.getEndPeriod().isBefore(request.getStartPeriod())) {
            throw new BadRequestException("endPeriod must not be before startPeriod");
        }

        AnalyticsQueryRequest query = AnalyticsQueryRequest.builder()
                .startDate(request.getStartPeriod())
                .endDate(request.getEndPeriod())
                .build();

        String csv;
        try {
            csv = switch (request.getReportType()) {
                case PLATFORM_COMPARISON -> {
                    List<PlatformMetricsResponse> metrics = analyticsQueryService.getPlatformComparison(userId, query);
                    yield CsvReportFormatter.forPlatformComparison(metrics);
                }
                case SUMMARY -> {
                    AnalyticsSummaryResponse summary = analyticsQueryService.getSummary(userId, query);
                    yield CsvReportFormatter.forSummary(summary);
                }
            };
        } catch (RuntimeException ex) {
            log.error("Analytics lookup failed while generating {} report for user {}: {}",
                    request.getReportType(), userId, ex.getMessage());
            reportRepository.save(Report.builder()
                    .userId(userId)
                    .reportType(request.getReportType())
                    .startPeriod(request.getStartPeriod())
                    .endPeriod(request.getEndPeriod())
                    .status(ReportStatus.FAILED)
                    .errorMessage("Failed to fetch analytics data")
                    .generatedAt(LocalDateTime.now())
                    .build());
            throw new ExternalApiException("Could not generate report: analytics data unavailable");
        }

        Report saved = reportRepository.save(Report.builder()
                .userId(userId)
                .reportType(request.getReportType())
                .startPeriod(request.getStartPeriod())
                .endPeriod(request.getEndPeriod())
                .status(ReportStatus.COMPLETED)
                .content(csv)
                .generatedAt(LocalDateTime.now())
                .build());

        log.info("Generated {} report {} for user {}", request.getReportType(), saved.getId(), userId);

        notificationService.create(userId, NotificationType.REPORT_READY,
                "Your " + request.getReportType() + " report for " + request.getStartPeriod()
                        + " to " + request.getEndPeriod() + " is ready.");

        return toResponse(saved);
    }

    @Override
    public PagedResponse<ReportSummaryResponse> getForUser(UUID userId, Pageable pageable) {
        return PagedResponse.of(reportRepository.findAllByUserId(userId, pageable)
                .map(r -> ReportSummaryResponse.builder()
                        .id(r.getId())
                        .reportType(r.getReportType())
                        .startPeriod(r.getStartPeriod())
                        .endPeriod(r.getEndPeriod())
                        .status(r.getStatus())
                        .generatedAt(r.getGeneratedAt())
                        .build()));
    }

    @Override
    public ReportResponse getById(UUID userId, UUID reportId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Report", reportId));
        return toResponse(report);
    }

    private ReportResponse toResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .reportType(report.getReportType())
                .startPeriod(report.getStartPeriod())
                .endPeriod(report.getEndPeriod())
                .status(report.getStatus())
                .content(report.getContent())
                .errorMessage(report.getErrorMessage())
                .generatedAt(report.getGeneratedAt())
                .build();
    }
}
